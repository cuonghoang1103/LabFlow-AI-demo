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

import java.util.List;
import java.util.Optional;

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

    private Equipment equipment(Long id, String serial) {
        Equipment equipment = new Equipment(); equipment.setId(id); equipment.setSerial(serial); equipment.setStatus(EquipmentStatus.AVAILABLE); return equipment;
    }
}
