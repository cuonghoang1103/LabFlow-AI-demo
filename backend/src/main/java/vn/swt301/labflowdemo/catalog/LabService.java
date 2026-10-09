package vn.swt301.labflowdemo.catalog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.swt301.labflowdemo.audit.AuditService;
import vn.swt301.labflowdemo.catalog.LabDtos.BuildingRequest;
import vn.swt301.labflowdemo.catalog.LabDtos.LabRequest;
import vn.swt301.labflowdemo.common.BusinessException;
import vn.swt301.labflowdemo.common.ErrorCode;
import vn.swt301.labflowdemo.common.PageResponse;
import vn.swt301.labflowdemo.common.Texts;
import vn.swt301.labflowdemo.settings.SettingsService;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Campus hierarchy: buildings and labs (E2 day 18, screens S13 + S14 - owner C2).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LabService {

    /** Codes are upper-case letters, digits and '-', 2-20 characters (e.g. AL-301). */
    static final Pattern CODE = Pattern.compile("^[A-Z0-9-]{2,20}$");
    static final int MIN_CAPACITY = 1;
    static final int MAX_CAPACITY = 200;
    static final int MIN_FLOOR = 0;
    static final int MAX_FLOOR = 50;

    private final CampusRepository campusRepository;
    private final BuildingRepository buildingRepository;
    private final LabRepository labRepository;
    private final SettingsService settings;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<Building> listBuildings() {
        return buildingRepository.findAll();
    }

    /**
     * Creates a building on the (single) campus.
     *
     * @throws BusinessException BUILDING_CODE_INVALID, LAB_NAME_INVALID, BUILDING_CODE_TAKEN
     */
    @Transactional
    public Building createBuilding(BuildingRequest request, Long actorId) {
        String code = normalizeCode(request.code());
        if (code == null || !CODE.matcher(code).matches()) {
            throw new BusinessException(ErrorCode.BUILDING_CODE_INVALID);
        }
        if (!Texts.lengthBetween(request.name(), 1, 100)) {
            throw new BusinessException(ErrorCode.LAB_NAME_INVALID);
        }
        if (buildingRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException(ErrorCode.BUILDING_CODE_TAKEN);
        }
        Building building = new Building();
        building.setCampusId(campusRepository.findAll().getFirst().getId());   // v1: một campus
        building.setCode(code);
        building.setName(Texts.clean(request.name()));
        building.setStatus(ActiveStatus.ACTIVE);
        buildingRepository.save(building);
        auditService.record(actorId, "BUILDING", building.getId(), "CREATE", null, building);
        log.info("Building created: id={}, code={}, by={}", building.getId(), code, actorId);
        return building;
    }

    /** S13: lab list with search / filter / sort / paging (page size capped at 100). */
    @Transactional(readOnly = true)
    public PageResponse<Lab> searchLabs(String q, Integer buildingId, ActiveStatus status, Integer minCapacity,
                                        Pageable pageable) {
        Pageable safe = pageable.getPageSize() > 100
                ? PageRequest.of(pageable.getPageNumber(), 100, pageable.getSort()) : pageable;
        return PageResponse.of(labRepository.search(Texts.clean(q), buildingId, status, minCapacity, safe), l -> l);
    }

    /** @throws BusinessException LAB_NOT_FOUND */
    @Transactional(readOnly = true)
    public Lab getLab(Integer id) {
        return labRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.LAB_NOT_FOUND));
    }

    /**
     * S14 Create lab. Code unique, name 1-100, floor 0-50, capacity 1-200, building must be ACTIVE.
     * BR-06: a lab with more seats than setting booking.approval-capacity (30) needs approval to book.
     *
     * @throws BusinessException BUILDING_NOT_FOUND, BUILDING_CLOSED, LAB_CODE_INVALID, LAB_NAME_INVALID,
     *                           LAB_FLOOR_INVALID, LAB_CAPACITY_INVALID, LAB_CODE_TAKEN
     */
    @Transactional
    public Lab createLab(LabRequest request, Long actorId) {
        checkBuildingActive(request.buildingId());
        String code = checkLabFields(request);
        if (labRepository.findByCodeIgnoreCase(code).isPresent()) {
            throw new BusinessException(ErrorCode.LAB_CODE_TAKEN);
        }
        Lab lab = new Lab();
        apply(lab, request, code);
        lab.setStatus(ActiveStatus.ACTIVE);
        labRepository.save(lab);
        auditService.record(actorId, "LAB", lab.getId(), "CREATE", null, lab);
        log.info("Lab created: id={}, code={}, capacity={}, requiresApproval={}, by={}",
                lab.getId(), code, lab.getCapacity(), lab.isRequiresApproval(), actorId);
        return lab;
    }

    /**
     * S14 Update lab. Same checks as create, plus optimistic locking: the client sends the version it
     * loaded; if someone saved in between, the update is refused instead of silently overwriting.
     *
     * @throws BusinessException LAB_NOT_FOUND, CONCURRENT_UPDATE, the create errors, LAB_CODE_TAKEN (other lab)
     */
    @Transactional
    public Lab updateLab(Integer id, LabRequest request, Long actorId) {
        Lab lab = getLab(id);
        if (request.version() == null || request.version() != lab.getVersion()) {
            throw new BusinessException(ErrorCode.CONCURRENT_UPDATE);
        }
        if (!lab.getBuildingId().equals(request.buildingId())) {
            checkBuildingActive(request.buildingId());
        }
        String code = checkLabFields(request);
        labRepository.findByCodeIgnoreCase(code)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new BusinessException(ErrorCode.LAB_CODE_TAKEN);
                });
        Map<String, Object> before = snapshot(lab);
        apply(lab, request, code);
        auditService.record(actorId, "LAB", id, "UPDATE", before, snapshot(lab));
        log.info("Lab updated: id={}, code={}, by={}", id, code, actorId);
        return lab;
    }

    /**
     * Opens or closes a lab. A CLOSED lab disappears from availability and cannot be booked.
     *
     * @throws BusinessException LAB_NOT_FOUND, LAB_STATUS_UNCHANGED
     */
    @Transactional
    public Lab changeLabStatus(Integer id, ActiveStatus status, Long actorId) {
        Lab lab = getLab(id);
        if (lab.getStatus() == status) {
            throw new BusinessException(ErrorCode.LAB_STATUS_UNCHANGED);
        }
        ActiveStatus before = lab.getStatus();
        lab.setStatus(status);
        auditService.record(actorId, "LAB", id, "STATUS", before.name(), status.name());
        log.info("Lab status changed: id={}, {} -> {}, by={}", id, before, status, actorId);
        return lab;
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private void checkBuildingActive(Integer buildingId) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUILDING_NOT_FOUND));
        if (building.getStatus() != ActiveStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.BUILDING_CLOSED);
        }
    }

    /** Validates code, name, floor, capacity; returns the normalized (trimmed, upper-case) code. */
    private String checkLabFields(LabRequest request) {
        String code = normalizeCode(request.code());
        if (code == null || !CODE.matcher(code).matches()) {
            throw new BusinessException(ErrorCode.LAB_CODE_INVALID);
        }
        if (!Texts.lengthBetween(request.name(), 1, 100)) {
            throw new BusinessException(ErrorCode.LAB_NAME_INVALID);
        }
        if (request.floor() == null || request.floor() < MIN_FLOOR || request.floor() > MAX_FLOOR) {
            throw new BusinessException(ErrorCode.LAB_FLOOR_INVALID);
        }
        if (request.capacity() == null || request.capacity() < MIN_CAPACITY || request.capacity() > MAX_CAPACITY) {
            throw new BusinessException(ErrorCode.LAB_CAPACITY_INVALID);
        }
        return code;
    }

    private void apply(Lab lab, LabRequest request, String code) {
        lab.setBuildingId(request.buildingId());
        lab.setCode(code);
        lab.setName(Texts.clean(request.name()));
        lab.setFloor(request.floor());
        lab.setCapacity(request.capacity());
        // BR-06: "lớn hơn" ngưỡng mới cần duyệt - đúng bằng ngưỡng thì KHÔNG cần
        lab.setRequiresApproval(request.capacity() > settings.getInt("booking.approval-capacity"));
    }

    private static Map<String, Object> snapshot(Lab lab) {
        return Map.of("buildingId", lab.getBuildingId(), "code", lab.getCode(), "name", lab.getName(),
                "floor", lab.getFloor(), "capacity", lab.getCapacity(), "requiresApproval", lab.isRequiresApproval());
    }

    static String normalizeCode(String raw) {
        String t = Texts.clean(raw);
        return t == null ? null : t.toUpperCase(Locale.ROOT);
    }
}
