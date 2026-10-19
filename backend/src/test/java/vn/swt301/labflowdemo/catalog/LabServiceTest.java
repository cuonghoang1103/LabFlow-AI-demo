package vn.swt301.labflowdemo.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.swt301.labflowdemo.audit.AuditService;
import vn.swt301.labflowdemo.common.BusinessException;
import vn.swt301.labflowdemo.common.ErrorCode;
import vn.swt301.labflowdemo.catalog.LabDtos.BuildingRequest;
import vn.swt301.labflowdemo.settings.SettingsService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabServiceTest {
    @Mock CampusRepository campusRepository;
    @Mock BuildingRepository buildingRepository;
    @Mock LabRepository labRepository;
    @Mock SettingsService settings;
    @Mock AuditService auditService;
    private LabService service;

    @BeforeEach
    void setUp() { service = new LabService(campusRepository, buildingRepository, labRepository, settings, auditService); }

    @Test
    void utcid13_N_createBuilding_createsActiveNormalizedBuilding() {
        // Arrange: one campus and an unused valid code.
        Campus campus = new Campus(); campus.setId(3);
        when(campusRepository.findAll()).thenReturn(List.of(campus));
        when(buildingRepository.existsByCodeIgnoreCase("ALPHA")).thenReturn(false);
        doAnswer(inv -> { ((Building) inv.getArgument(0)).setId(12); return inv.getArgument(0); }).when(buildingRepository).save(any());
        // Condition: create a building with whitespace around its fields.
        Building result = service.createBuilding(new BuildingRequest(" alpha ", " Alpha Building "), 77L);
        // Assert/Confirmation: normalized active building is saved and audited.
        assertAll(() -> assertEquals(3, result.getCampusId()), () -> assertEquals("ALPHA", result.getCode()),
                () -> assertEquals("Alpha Building", result.getName()), () -> assertEquals(ActiveStatus.ACTIVE, result.getStatus()));
        verify(buildingRepository).save(result);
        verify(auditService).record(77L, "BUILDING", 12, "CREATE", null, result);
    }

    @Test
    void utcid14_B_createBuilding_rejectsInvalidCode() {
        // Arrange: malformed building code.
        // Condition: attempt creation.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createBuilding(new BuildingRequest("a!", "Name"), 1L));
        // Assert/Confirmation: BUILDING_CODE_INVALID is returned before data access.
        assertEquals(ErrorCode.BUILDING_CODE_INVALID, ex.getCode());
        verifyNoInteractions(campusRepository, buildingRepository);
    }

    @Test
    void utcid15_B_createBuilding_rejectsTakenCode() {
        // Arrange: valid code already belongs to another building.
        when(buildingRepository.existsByCodeIgnoreCase("ALPHA")).thenReturn(true);
        // Condition: attempt duplicate creation.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createBuilding(new BuildingRequest("ALPHA", "Name"), 1L));
        // Assert/Confirmation: BUILDING_CODE_TAKEN is returned.
        assertEquals(ErrorCode.BUILDING_CODE_TAKEN, ex.getCode());
        verifyNoInteractions(campusRepository);
    }
}
