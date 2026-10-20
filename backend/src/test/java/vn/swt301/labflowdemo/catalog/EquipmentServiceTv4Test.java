package vn.swt301.labflowdemo.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.swt301.labflowdemo.audit.AuditService;
import vn.swt301.labflowdemo.catalog.EquipmentDtos.EquipmentRequest;
import vn.swt301.labflowdemo.common.BusinessException;
import vn.swt301.labflowdemo.common.ErrorCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquipmentServiceTv4Test {
    @Mock EquipmentRepository equipmentRepository;
    @Mock EquipmentTypeRepository typeRepository;
    @Mock LabRepository labRepository;
    @Mock AuditService auditService;
    private EquipmentService service;

    @BeforeEach
    void setUp() { service = new EquipmentService(equipmentRepository, typeRepository, labRepository, auditService); }
    private EquipmentRequest request(String serial) { return new EquipmentRequest(10, 3, serial, "Oscilloscope", true, false); }
    private Lab activeLab() { Lab l = new Lab(); l.setId(10); l.setStatus(ActiveStatus.ACTIVE); return l; }

    @Test
    void utcid51_N_createEquipment_createsAvailableNormalizedSerial() {
        // Arrange: valid type and active lab exist; serial is unused.
        when(typeRepository.existsById(3)).thenReturn(true);
        when(labRepository.findById(10)).thenReturn(Optional.of(activeLab()));
        when(equipmentRepository.findBySerialIgnoreCase("SN-001")).thenReturn(Optional.empty());
        doAnswer(inv -> { ((Equipment) inv.getArgument(0)).setId(31L); return inv.getArgument(0); }).when(equipmentRepository).save(any());
        // Condition: create with a lowercase, padded serial.
        Equipment result = service.createEquipment(request(" sn-001 "), 8L);
        // Confirmation: serial is normalized and initial status is AVAILABLE.
        assertAll(() -> assertEquals("SN-001", result.getSerial()), () -> assertEquals(EquipmentStatus.AVAILABLE, result.getStatus()),
                () -> assertTrue(result.isRequiresTraining()));
        verify(equipmentRepository).save(result);
        verify(auditService).record(8L, "EQUIPMENT", 31L, "CREATE", null, result);
    }

    @Test
    void utcid52_B_createEquipment_rejectsInactiveLabWithErrorCode() {
        // Arrange: type exists but the target lab is inactive.
        Lab lab = activeLab(); lab.setStatus(ActiveStatus.CLOSED);
        when(typeRepository.existsById(3)).thenReturn(true);
        when(labRepository.findById(10)).thenReturn(Optional.of(lab));
        // Condition: attempt equipment creation.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createEquipment(request("SN-001"), 8L));
        // Confirmation: LAB_INACTIVE is specific and no serial lookup occurs.
        assertEquals(ErrorCode.LAB_INACTIVE, ex.getCode());
        verify(equipmentRepository, never()).save(any());
    }

    @Test
    void utcid53_A_changeStatus_movesAvailableToMaintenance() {
        // Arrange: an available unit exists.
        Equipment equipment = new Equipment(); equipment.setId(31L); equipment.setSerial("SN-001"); equipment.setStatus(EquipmentStatus.AVAILABLE);
        when(equipmentRepository.findById(31L)).thenReturn(Optional.of(equipment));
        // Condition: administrator supplies the required maintenance reason.
        Equipment result = service.changeStatus(31L, EquipmentStatus.MAINTENANCE, "Calibration", 8L);
        // Confirmation: status and reason are changed and audited.
        assertAll(() -> assertEquals(EquipmentStatus.MAINTENANCE, result.getStatus()), () -> assertEquals("Calibration", result.getStatusReason()));
        verify(auditService).record(8L, "EQUIPMENT", 31L, "STATUS", "AVAILABLE", "MAINTENANCE");
    }

    @Test
    void utcid54_B_changeStatus_requiresReasonForRetirement() {
        // Arrange: an available unit can be retired only with a reason.
        Equipment equipment = new Equipment(); equipment.setId(31L); equipment.setSerial("SN-001"); equipment.setStatus(EquipmentStatus.AVAILABLE);
        when(equipmentRepository.findById(31L)).thenReturn(Optional.of(equipment));
        // Condition: request retirement with blank reason.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.changeStatus(31L, EquipmentStatus.RETIRED, "  ", 8L));
        // Confirmation: EQUIPMENT_REASON_REQUIRED preserves the current status.
        assertEquals(ErrorCode.EQUIPMENT_REASON_REQUIRED, ex.getCode());
        assertEquals(EquipmentStatus.AVAILABLE, equipment.getStatus());
    }

    @Test
    void utcid55_N_bulkChangeStatus_changesAllUnitsAndReturnsCount() {
        // Arrange: two available units are selected for maintenance.
        Equipment first = equipment(31L, "SN-001");
        Equipment second = equipment(32L, "SN-002");
        when(equipmentRepository.findAllById(List.of(31L, 32L))).thenReturn(List.of(first, second));
        // Condition: apply one valid status change to the whole selection.
        int count = service.bulkChangeStatus(new java.util.ArrayList<>(List.of(31L, 32L)), EquipmentStatus.MAINTENANCE, "Service", 8L);
        // Confirmation: all units change and the returned count matches the selection.
        assertEquals(2, count);
        assertEquals(EquipmentStatus.MAINTENANCE, first.getStatus());
        assertEquals(EquipmentStatus.MAINTENANCE, second.getStatus());
        verify(auditService, times(2)).record(eq(8L), eq("EQUIPMENT"), anyLong(), eq("STATUS"), eq("AVAILABLE"), eq("MAINTENANCE"));
    }

    @Test
    void utcid56_B_bulkChangeStatus_rejectsDuplicateIdsWithErrorCode() {
        // Arrange: the request contains a duplicate identifier.
        // Condition: attempt a bulk status transition.
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.bulkChangeStatus(new java.util.ArrayList<>(List.of(31L, 31L)), EquipmentStatus.MAINTENANCE, "Service", 8L));
        // Confirmation: EQUIPMENT_BULK_INVALID is raised before repository access.
        assertEquals(ErrorCode.EQUIPMENT_BULK_INVALID, ex.getCode());
        verifyNoInteractions(equipmentRepository, auditService);
    }

    @Test void createEquipment_duplicateSerialRejectsWithoutSaving() {
        // Condition: normalized serial already belongs to another unit.
        when(typeRepository.existsById(3)).thenReturn(true);
        when(labRepository.findById(10)).thenReturn(Optional.of(activeLab()));
        when(equipmentRepository.findBySerialIgnoreCase("SN-001")).thenReturn(Optional.of(equipment(31L, "SN-001")));
        // Act: create with the same serial in another letter case.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createEquipment(request(" sn-001 "), 8L));
        // Confirmation: uniqueness is enforced before writing or auditing.
        assertEquals(ErrorCode.EQUIPMENT_SERIAL_TAKEN, ex.getCode());
        verify(equipmentRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test void changeStatus_sameStatusRejectsWithoutAudit() {
        // Condition: an AVAILABLE unit is requested to stay AVAILABLE.
        Equipment unit = equipment(31L, "SN-001");
        when(equipmentRepository.findById(31L)).thenReturn(Optional.of(unit));
        // Act: attempt the redundant transition.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.changeStatus(
                31L, EquipmentStatus.AVAILABLE, "No change", 8L));
        // Confirmation: same-state transition is invalid and not audited.
        assertEquals(ErrorCode.EQUIPMENT_INVALID_TRANSITION, ex.getCode());
        assertEquals(EquipmentStatus.AVAILABLE, unit.getStatus());
        verifyNoInteractions(auditService);
    }

    @Test void changeStatus_reasonOver255RejectsWithoutMutation() {
        // Condition: maintenance reason exceeds its 255-character boundary by one.
        Equipment unit = equipment(31L, "SN-001");
        when(equipmentRepository.findById(31L)).thenReturn(Optional.of(unit));
        // Act: request maintenance with a 256-character reason.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.changeStatus(
                31L, EquipmentStatus.MAINTENANCE, "a".repeat(256), 8L));
        // Confirmation: status stays AVAILABLE, without audit.
        assertEquals(ErrorCode.EQUIPMENT_REASON_REQUIRED, ex.getCode());
        assertEquals(EquipmentStatus.AVAILABLE, unit.getStatus());
        verifyNoInteractions(auditService);
    }

    @Test void bulkChangeStatus_missingUnitRejectsBeforeUpdatingAny() {
        // Condition: repository finds only one of the two requested equipment IDs.
        Equipment first = equipment(31L, "SN-001");
        List<Long> ids = new java.util.ArrayList<>(List.of(31L, 32L));
        when(equipmentRepository.findAllById(ids)).thenReturn(List.of(first));
        // Act: change both units in one request using the mutable list accepted by the current implementation.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.bulkChangeStatus(
                ids, EquipmentStatus.MAINTENANCE, "Service", 8L));
        // Confirmation: count mismatch rejects before transitioning the available unit.
        assertEquals(ErrorCode.EQUIPMENT_NOT_FOUND, ex.getCode());
        assertEquals(EquipmentStatus.AVAILABLE, first.getStatus());
        verifyNoInteractions(auditService);
    }

    @Test void createEquipment_serialLengthBoundaries() {
        // Condition: serials of length 2 and 51 are invalid; 3 and 50 are valid.
        for (int length : new int[] {2, 51}) {
            // Act: attempt creation outside the allowed range.
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.createEquipment(request("A".repeat(length)), 8L));
            // Confirmation: reject before any repository access.
            assertEquals(ErrorCode.EQUIPMENT_SERIAL_INVALID, ex.getCode());
        }
        verifyNoInteractions(typeRepository, labRepository, equipmentRepository, auditService);
        when(typeRepository.existsById(3)).thenReturn(true);
        when(labRepository.findById(10)).thenReturn(Optional.of(activeLab()));
        for (int length : new int[] {3, 50}) {
            // Act: create at each inclusive serial length boundary.
            Equipment result = service.createEquipment(request("a".repeat(length)), 8L);
            // Confirmation: the exact boundary length is accepted and normalized.
            assertEquals("A".repeat(length), result.getSerial());
            assertEquals(EquipmentStatus.AVAILABLE, result.getStatus());
            verify(equipmentRepository).save(result);
        }
    }

    @Test void changeStatus_coversBr11TransitionMatrix() {
        // Condition: each of the four source states is tried against each of the four targets.
        for (EquipmentStatus from : EquipmentStatus.values()) {
            for (EquipmentStatus to : EquipmentStatus.values()) {
                Equipment unit = equipment(31L, "SN-001");
                unit.setStatus(from);
                when(equipmentRepository.findById(31L)).thenReturn(Optional.of(unit));
                boolean allowed = (from == EquipmentStatus.AVAILABLE || from == EquipmentStatus.MAINTENANCE)
                        && to != from && to != EquipmentStatus.ON_LOAN;
                // Act: request the transition with a reason acceptable for maintenance/retirement.
                if (allowed) {
                    Equipment result = service.changeStatus(31L, to, "x", 8L);
                    // Confirmation: permitted transitions reach the requested state.
                    assertEquals(to, result.getStatus(), from + " -> " + to);
                } else {
                    BusinessException ex = assertThrows(BusinessException.class,
                            () -> service.changeStatus(31L, to, "x", 8L), from + " -> " + to);
                    // Confirmation: RETIRED is terminal; ON_LOAN cannot be changed manually.
                    assertEquals(from == EquipmentStatus.RETIRED ? ErrorCode.EQUIPMENT_RETIRED
                            : ErrorCode.EQUIPMENT_INVALID_TRANSITION, ex.getCode(), from + " -> " + to);
                    assertEquals(from, unit.getStatus());
                }
            }
        }
    }

    @Test void changeStatus_acceptsReasonAt255() {
        // Condition: maintenance reason has exactly 255 characters.
        Equipment unit = equipment(31L, "SN-001");
        when(equipmentRepository.findById(31L)).thenReturn(Optional.of(unit));
        // Act: request maintenance with the maximum reason length.
        service.changeStatus(31L, EquipmentStatus.MAINTENANCE, "a".repeat(255), 8L);
        // Confirmation: the reason and transition are retained.
        assertEquals(EquipmentStatus.MAINTENANCE, unit.getStatus());
        assertEquals(255, unit.getStatusReason().length());
    }

    @Test void bulkChangeStatus_rejectsNullEmptyOverLimitAndNullElement() {
        // Condition: invalid selections are null, empty, 51 IDs or a null element.
        List<List<Long>> invalid = new ArrayList<>();
        invalid.add(null);
        invalid.add(new ArrayList<>());
        invalid.add(new ArrayList<>(LongStream.rangeClosed(1, 51).boxed().toList()));
        ArrayList<Long> withNull = new ArrayList<>();
        withNull.add(31L);
        withNull.add(null);
        invalid.add(withNull);
        for (List<Long> ids : invalid) {
            // Act: attempt the invalid bulk selection.
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.bulkChangeStatus(ids, EquipmentStatus.MAINTENANCE, "Service", 8L));
            // Confirmation: each fails validation without reading or changing units.
            assertEquals(ErrorCode.EQUIPMENT_BULK_INVALID, ex.getCode());
        }
        verifyNoInteractions(equipmentRepository, auditService);
    }

    @Test void bulkChangeStatus_acceptsOneAndFiftyIds() {
        // Condition: mutable selections of one and fifty distinct equipment IDs.
        for (int size : new int[] {1, 50}) {
            ArrayList<Long> ids = new ArrayList<>(LongStream.rangeClosed(1, size).boxed().toList());
            List<Equipment> units = ids.stream().map(id -> equipment(id, "SN-" + id)).toList();
            when(equipmentRepository.findAllById(ids)).thenReturn(units);
            // Act: change all selected units to maintenance.
            int count = service.bulkChangeStatus(ids, EquipmentStatus.MAINTENANCE, "Service", 8L);
            // Confirmation: all requested units change, including the upper size boundary.
            assertEquals(size, count);
            for (Equipment unit : units) assertEquals(EquipmentStatus.MAINTENANCE, unit.getStatus());
        }
    }

    @Test void bulkChangeStatus_retiredFirstRejectsBeforeMutatingAvailableUnit() {
        // Condition: a RETIRED unit precedes an AVAILABLE unit in a mutable selection.
        ArrayList<Long> ids = new ArrayList<>(List.of(31L, 32L));
        Equipment retired = equipment(31L, "SN-001");
        retired.setStatus(EquipmentStatus.RETIRED);
        Equipment available = equipment(32L, "SN-002");
        when(equipmentRepository.findAllById(ids)).thenReturn(List.of(retired, available));
        // Act: request maintenance for both units.
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.bulkChangeStatus(ids, EquipmentStatus.MAINTENANCE, "Service", 8L));
        // Confirmation: the invalid unit rejects the batch before either unit changes.
        assertEquals(ErrorCode.EQUIPMENT_RETIRED, ex.getCode());
        assertEquals(EquipmentStatus.RETIRED, retired.getStatus());
        assertEquals(EquipmentStatus.AVAILABLE, available.getStatus());
        verifyNoInteractions(auditService);
    }

    private Equipment equipment(Long id, String serial) {
        Equipment equipment = new Equipment(); equipment.setId(id); equipment.setSerial(serial); equipment.setStatus(EquipmentStatus.AVAILABLE); return equipment;
    }
}
