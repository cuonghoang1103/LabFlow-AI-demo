package vn.swt301.labflowdemo.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.swt301.labflowdemo.audit.AuditService;
import vn.swt301.labflowdemo.catalog.LabDtos.LabRequest;
import vn.swt301.labflowdemo.common.BusinessException;
import vn.swt301.labflowdemo.common.ErrorCode;
import vn.swt301.labflowdemo.settings.SettingsService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabServiceTv4Test {
    @Mock BuildingRepository buildingRepository;
    @Mock LabRepository labRepository;
    @Mock CampusRepository campusRepository;
    @Mock SettingsService settings;
    @Mock AuditService auditService;
    private LabService service;

    @BeforeEach
    void setUp() { service = new LabService(campusRepository, buildingRepository, labRepository, settings, auditService); }

    private LabRequest request(Integer version) { return new LabRequest(10, " al-301 ", " Physics Lab ", 3, 40, version); }
    private Building activeBuilding() { Building b = new Building(); b.setId(10); b.setStatus(ActiveStatus.ACTIVE); return b; }

    @Test
    void utcid41_N_createLab_createsActiveAndApprovalRequired() {
        // Arrange: active building, unused code, and approval threshold of 30 seats.
        when(buildingRepository.findById(10)).thenReturn(Optional.of(activeBuilding()));
        when(labRepository.findByCodeIgnoreCase("AL-301")).thenReturn(Optional.empty());
        when(settings.getInt("booking.approval-capacity")).thenReturn(30);
        doAnswer(inv -> { ((Lab) inv.getArgument(0)).setId(21); return inv.getArgument(0); }).when(labRepository).save(any());
        // Condition: create a valid lab request with surrounding whitespace.
        Lab result = service.createLab(request(null), 7L);
        // Confirmation: normalized active lab is saved and needs approval above threshold.
        assertAll(() -> assertEquals("AL-301", result.getCode()), () -> assertEquals("Physics Lab", result.getName()),
                () -> assertEquals(ActiveStatus.ACTIVE, result.getStatus()), () -> assertTrue(result.isRequiresApproval()));
        verify(labRepository).save(result);
        verify(auditService).record(7L, "LAB", 21, "CREATE", null, result);
    }

    @Test
    void utcid42_B_createLab_rejectsClosedBuildingWithErrorCode() {
        // Arrange: requested building exists but is closed.
        Building building = activeBuilding(); building.setStatus(ActiveStatus.CLOSED);
        when(buildingRepository.findById(10)).thenReturn(Optional.of(building));
        // Condition: attempt to create a lab in the closed building.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createLab(request(null), 7L));
        // Confirmation: BUILDING_CLOSED is raised before lab validation or persistence.
        assertEquals(ErrorCode.BUILDING_CLOSED, ex.getCode());
        verifyNoInteractions(labRepository, settings, auditService);
    }

    @Test
    void utcid43_A_createLab_rejectsDuplicateCodeWithErrorCode() {
        // Arrange: active building and an existing lab using the normalized code.
        when(buildingRepository.findById(10)).thenReturn(Optional.of(activeBuilding()));
        when(labRepository.findByCodeIgnoreCase("AL-301")).thenReturn(Optional.of(new Lab()));
        // Condition: attempt to create the duplicate lab.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createLab(request(null), 7L));
        // Confirmation: LAB_CODE_TAKEN is returned and no entity is saved.
        assertEquals(ErrorCode.LAB_CODE_TAKEN, ex.getCode());
        verify(labRepository, never()).save(any());
    }

    @Test
    void utcid44_N_updateLab_updatesSameLabWithMatchingVersion() {
        // Arrange: active existing lab at version 2; its own code is not a conflict.
        Lab lab = new Lab(); lab.setId(21); lab.setBuildingId(10); lab.setCode("AL-301"); lab.setName("Physics Lab"); lab.setFloor(3); lab.setCapacity(20); lab.setRequiresApproval(false); lab.setVersion(2); lab.setStatus(ActiveStatus.ACTIVE);
        when(labRepository.findById(21)).thenReturn(Optional.of(lab));
        when(labRepository.findByCodeIgnoreCase("AL-301")).thenReturn(Optional.of(lab));
        when(settings.getInt("booking.approval-capacity")).thenReturn(50);
        // Condition: update with the version read by the client.
        Lab result = service.updateLab(21, request(2), 7L);
        // Confirmation: fields are changed and exact version is accepted.
        assertAll(() -> assertSame(lab, result), () -> assertEquals("AL-301", result.getCode()),
                () -> assertEquals(40, result.getCapacity()), () -> assertFalse(result.isRequiresApproval()));
        verify(auditService).record(eq(7L), eq("LAB"), eq(21), eq("UPDATE"), any(), any());
    }

    @Test
    void utcid45_B_updateLab_rejectsStaleVersionWithConcurrentUpdate() {
        // Arrange: persisted lab is newer than the request version.
        Lab lab = new Lab(); lab.setId(21); lab.setBuildingId(10); lab.setVersion(3);
        when(labRepository.findById(21)).thenReturn(Optional.of(lab));
        // Condition: submit an update based on stale version 2.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateLab(21, request(2), 7L));
        // Confirmation: CONCURRENT_UPDATE protects the newer record.
        assertEquals(ErrorCode.CONCURRENT_UPDATE, ex.getCode());
        verifyNoInteractions(buildingRepository, settings, auditService);
    }

    @Test
    void utcid46_A_updateLab_rejectsCodeTakenByAnotherLab() {
        // Arrange: current lab and another lab already own the requested code.
        Lab current = new Lab(); current.setId(21); current.setBuildingId(10); current.setVersion(1);
        Lab other = new Lab(); other.setId(22);
        when(labRepository.findById(21)).thenReturn(Optional.of(current));
        when(labRepository.findByCodeIgnoreCase("AL-301")).thenReturn(Optional.of(other));
        // Condition: attempt the otherwise valid update.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateLab(21, request(1), 7L));
        // Confirmation: LAB_CODE_TAKEN identifies the conflicting other lab.
        assertEquals(ErrorCode.LAB_CODE_TAKEN, ex.getCode());
        verify(auditService, never()).record(anyLong(), anyString(), any(), anyString(), any(), any());
    }

    @Test void createLab_atApprovalThresholdDoesNotRequireApproval() {
        // Condition: capacity equals the configured threshold, not greater than it.
        when(buildingRepository.findById(10)).thenReturn(Optional.of(activeBuilding()));
        when(labRepository.findByCodeIgnoreCase("AL-301")).thenReturn(Optional.empty());
        when(settings.getInt("booking.approval-capacity")).thenReturn(30);
        // Act: create the lab at capacity 30.
        Lab result = service.createLab(new LabRequest(10, "AL-301", "Physics", 0, 30, null), 7L);
        // Confirmation: floor zero is valid and approval is not required at the boundary.
        assertEquals(0, result.getFloor());
        assertFalse(result.isRequiresApproval());
        verify(labRepository).save(result);
    }

    @Test void createLab_aboveApprovalThresholdRequiresApproval() {
        // Condition: 31 seats is one above the BR-06 threshold of 30.
        when(buildingRepository.findById(10)).thenReturn(Optional.of(activeBuilding()));
        when(settings.getInt("booking.approval-capacity")).thenReturn(30);
        // Act: create the lab at the first capacity requiring approval.
        Lab result = service.createLab(new LabRequest(10, "AL-301", "Physics", 50, 31, null), 7L);
        // Confirmation: floor 50 is valid and approval is required.
        assertEquals("AL-301", result.getCode());
        assertEquals(50, result.getFloor());
        assertTrue(result.isRequiresApproval());
        verify(labRepository).save(result);
    }

    @Test void createLab_aboveMaximumCapacityRejectsWithoutSaving() {
        // Condition: 201 exceeds the maximum capacity of 200.
        when(buildingRepository.findById(10)).thenReturn(Optional.of(activeBuilding()));
        // Act: submit the out-of-range request.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createLab(
                new LabRequest(10, "AL-301", "Physics", 50, 201, null), 7L));
        // Confirmation: no persistence or audit occurs.
        assertEquals(ErrorCode.LAB_CAPACITY_INVALID, ex.getCode());
        verifyNoInteractions(labRepository, settings, auditService);
    }

    @Test void createLab_belowMinimumCapacityRejectsWithoutSaving() {
        // Condition: capacity zero falls below the minimum of one.
        when(buildingRepository.findById(10)).thenReturn(Optional.of(activeBuilding()));
        // Act: try to create a zero-capacity lab.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createLab(
                new LabRequest(10, "AL-301", "Physics", 0, 0, null), 7L));
        // Confirmation: reject before duplicate lookup, save or audit.
        assertEquals(ErrorCode.LAB_CAPACITY_INVALID, ex.getCode());
        verifyNoInteractions(labRepository, settings, auditService);
    }

    @Test void updateLab_missingVersionRejectsBeforeChangingAnything() {
        // Condition: the existing lab has a version, but the client omitted it.
        Lab lab = new Lab(); lab.setId(21); lab.setVersion(2); lab.setBuildingId(10);
        when(labRepository.findById(21)).thenReturn(Optional.of(lab));
        // Act: attempt update without optimistic-lock version.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateLab(21, request(null), 7L));
        // Confirmation: the request cannot overwrite the existing lab.
        assertEquals(ErrorCode.CONCURRENT_UPDATE, ex.getCode());
        verifyNoInteractions(buildingRepository, settings, auditService);
    }

    @Test void updateLab_movingToClosedBuildingRejectsBeforeChangingLab() {
        // Condition: request moves an existing lab to a different, CLOSED building.
        Lab lab = new Lab(); lab.setId(21); lab.setVersion(2); lab.setBuildingId(11); lab.setCode("OLD");
        Building closed = activeBuilding(); closed.setStatus(ActiveStatus.CLOSED);
        when(labRepository.findById(21)).thenReturn(Optional.of(lab));
        when(buildingRepository.findById(10)).thenReturn(Optional.of(closed));
        // Act: submit a matching-version update to building 10.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateLab(21, request(2), 7L));
        // Confirmation: original building and code remain unchanged, no audit occurs.
        assertEquals(ErrorCode.BUILDING_CLOSED, ex.getCode());
        assertEquals(11, lab.getBuildingId());
        assertEquals("OLD", lab.getCode());
        verifyNoInteractions(settings, auditService);
    }
}
