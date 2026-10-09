package vn.swt301.labflowdemo.catalog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.swt301.labflowdemo.audit.AuditService;
import vn.swt301.labflowdemo.catalog.EquipmentDtos.EquipmentRequest;
import vn.swt301.labflowdemo.common.BusinessException;
import vn.swt301.labflowdemo.common.ErrorCode;
import vn.swt301.labflowdemo.common.PageResponse;
import vn.swt301.labflowdemo.common.Texts;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Equipment units (ResourceUnit) - E3 day 22, screens S16 list + S17 detail (owner C4). BR-11.
 * <p>
 * Status rules (bảng chuyển trạng thái):
 * <pre>
 *   AVAILABLE   -> MAINTENANCE (reason) | RETIRED (reason)
 *   MAINTENANCE -> AVAILABLE           | RETIRED (reason)
 *   ON_LOAN     -> (only the loan module changes it)
 *   RETIRED     -> (final, nothing)
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EquipmentService {

    static final Pattern SERIAL = Pattern.compile("^[A-Z0-9_-]{3,50}$");
    static final int MAX_BULK = 50;

    private final EquipmentRepository equipmentRepository;
    private final EquipmentTypeRepository typeRepository;
    private final LabRepository labRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<EquipmentType> listTypes() {
        return typeRepository.findAll();
    }

    /** S16: equipment list - search serial/name, filter lab/type/status, server-side paging. */
    @Transactional(readOnly = true)
    public PageResponse<Equipment> searchEquipment(String q, Integer labId, Integer typeId, EquipmentStatus status,
                                                   Pageable pageable) {
        Pageable safe = pageable.getPageSize() > 100
                ? PageRequest.of(pageable.getPageNumber(), 100, pageable.getSort()) : pageable;
        return PageResponse.of(equipmentRepository.search(Texts.orEmpty(q), labId, typeId, status, safe), e -> e);
    }

    /** @throws BusinessException EQUIPMENT_NOT_FOUND */
    @Transactional(readOnly = true)
    public Equipment getEquipment(Long id) {
        return equipmentRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.EQUIPMENT_NOT_FOUND));
    }

    /**
     * S17 Create equipment: unique serial (stored upper-case), type exists, lab exists and ACTIVE.
     * New equipment starts AVAILABLE.
     *
     * @throws BusinessException EQUIPMENT_SERIAL_INVALID, EQUIPMENT_NAME_INVALID, EQUIPMENT_TYPE_NOT_FOUND,
     *                           LAB_NOT_FOUND, LAB_INACTIVE, EQUIPMENT_SERIAL_TAKEN
     */
    @Transactional
    public Equipment createEquipment(EquipmentRequest request, Long actorId) {
        String serial = checkFields(request);
        if (equipmentRepository.findBySerialIgnoreCase(serial).isPresent()) {
            throw new BusinessException(ErrorCode.EQUIPMENT_SERIAL_TAKEN);
        }
        Equipment equipment = new Equipment();
        apply(equipment, request, serial);
        equipment.setStatus(EquipmentStatus.AVAILABLE);
        equipmentRepository.save(equipment);
        auditService.record(actorId, "EQUIPMENT", equipment.getId(), "CREATE", null, equipment);
        log.info("Equipment created: id={}, serial={}, labId={}, by={}", equipment.getId(), serial,
                equipment.getLabId(), actorId);
        return equipment;
    }

    /**
     * S17 Update equipment details. Retired equipment is read-only; equipment on loan cannot move to
     * another lab (it is not physically there).
     *
     * @throws BusinessException EQUIPMENT_NOT_FOUND, EQUIPMENT_RETIRED, EQUIPMENT_INVALID_TRANSITION,
     *                           the create errors, EQUIPMENT_SERIAL_TAKEN (other unit)
     */
    @Transactional
    public Equipment updateEquipment(Long id, EquipmentRequest request, Long actorId) {
        Equipment equipment = getEquipment(id);
        if (equipment.getStatus() == EquipmentStatus.RETIRED) {
            throw new BusinessException(ErrorCode.EQUIPMENT_RETIRED);
        }
        if (equipment.getStatus() == EquipmentStatus.ON_LOAN && !equipment.getLabId().equals(request.labId())) {
            throw new BusinessException(ErrorCode.EQUIPMENT_INVALID_TRANSITION, "Equipment on loan cannot change lab");
        }
        String serial = checkFields(request);
        equipmentRepository.findBySerialIgnoreCase(serial)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new BusinessException(ErrorCode.EQUIPMENT_SERIAL_TAKEN);
                });
        Map<String, Object> before = Map.of("labId", equipment.getLabId(), "serial", equipment.getSerial(),
                "name", equipment.getName());
        apply(equipment, request, serial);
        auditService.record(actorId, "EQUIPMENT", id, "UPDATE", before, equipment);
        log.info("Equipment updated: id={}, serial={}, by={}", id, serial, actorId);
        return equipment;
    }

    /**
     * S16 Change the status of one unit, following the transition table above (BR-11).
     *
     * @throws BusinessException EQUIPMENT_NOT_FOUND, EQUIPMENT_RETIRED, EQUIPMENT_INVALID_TRANSITION,
     *                           EQUIPMENT_REASON_REQUIRED
     */
    @Transactional
    public Equipment changeStatus(Long id, EquipmentStatus newStatus, String reason, Long actorId) {
        Equipment equipment = getEquipment(id);
        applyTransition(equipment, newStatus, reason, actorId);
        return equipment;
    }

    /**
     * S16 Change the status of several units at once - ALL or NOTHING: if one unit cannot change,
     * the exception rolls back the whole transaction and no unit is changed.
     *
     * @return number of units changed
     * @throws BusinessException EQUIPMENT_BULK_INVALID, EQUIPMENT_NOT_FOUND, plus the single-unit errors
     */
    @Transactional
    public int bulkChangeStatus(List<Long> ids, EquipmentStatus newStatus, String reason, Long actorId) {
        if (ids == null || ids.isEmpty() || ids.size() > MAX_BULK || ids.contains(null)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new BusinessException(ErrorCode.EQUIPMENT_BULK_INVALID);
        }
        List<Equipment> units = equipmentRepository.findAllById(ids);
        if (units.size() != ids.size()) {
            throw new BusinessException(ErrorCode.EQUIPMENT_NOT_FOUND);
        }
        for (Equipment unit : units) {
            applyTransition(unit, newStatus, reason, actorId);
        }
        log.info("Equipment bulk status: count={}, to={}, by={}", units.size(), newStatus, actorId);
        return units.size();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private void applyTransition(Equipment equipment, EquipmentStatus to, String reason, Long actorId) {
        EquipmentStatus from = equipment.getStatus();
        if (from == EquipmentStatus.RETIRED) {
            throw new BusinessException(ErrorCode.EQUIPMENT_RETIRED);
        }
        // ON_LOAN chỉ do module mượn/trả đặt và gỡ - quản trị viên không đổi tay
        if (to == from || to == EquipmentStatus.ON_LOAN || from == EquipmentStatus.ON_LOAN) {
            throw new BusinessException(ErrorCode.EQUIPMENT_INVALID_TRANSITION,
                    "Cannot change equipment " + equipment.getSerial() + " from " + from + " to " + to);
        }
        String cleanReason = Texts.clean(reason);
        boolean needsReason = to == EquipmentStatus.MAINTENANCE || to == EquipmentStatus.RETIRED;
        if (needsReason && (cleanReason == null || cleanReason.length() > 255)) {
            throw new BusinessException(ErrorCode.EQUIPMENT_REASON_REQUIRED);
        }
        equipment.setStatus(to);
        equipment.setStatusReason(cleanReason);
        auditService.record(actorId, "EQUIPMENT", equipment.getId(), "STATUS", from.name(), to.name());
        log.info("Equipment status changed: id={}, {} -> {}, by={}", equipment.getId(), from, to, actorId);
    }

    /** Validates serial/name/type/lab and returns the normalized serial. */
    private String checkFields(EquipmentRequest request) {
        String serial = Texts.clean(request.serial());
        serial = serial == null ? null : serial.toUpperCase(Locale.ROOT);
        if (serial == null || !SERIAL.matcher(serial).matches()) {
            throw new BusinessException(ErrorCode.EQUIPMENT_SERIAL_INVALID);
        }
        if (!Texts.lengthBetween(request.name(), 1, 100)) {
            throw new BusinessException(ErrorCode.EQUIPMENT_NAME_INVALID);
        }
        if (request.typeId() == null || !typeRepository.existsById(request.typeId())) {
            throw new BusinessException(ErrorCode.EQUIPMENT_TYPE_NOT_FOUND);
        }
        Lab lab = request.labId() == null ? null : labRepository.findById(request.labId()).orElse(null);
        if (lab == null) {
            throw new BusinessException(ErrorCode.LAB_NOT_FOUND);
        }
        if (lab.getStatus() != ActiveStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.LAB_INACTIVE);
        }
        return serial;
    }

    private void apply(Equipment equipment, EquipmentRequest request, String serial) {
        equipment.setLabId(request.labId());
        equipment.setTypeId(request.typeId());
        equipment.setSerial(serial);
        equipment.setName(Texts.clean(request.name()));
        equipment.setRequiresTraining(request.requiresTraining());
        equipment.setRequiresApproval(request.requiresApproval());
    }
}
