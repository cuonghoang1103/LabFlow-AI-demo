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

    @Test
    void utcid29_N_createBuilding_acceptsTwoCharacterCode() {
        // Condition: minimum-length code and one campus exist.
        Campus campus = new Campus(); campus.setId(3);
        when(campusRepository.findAll()).thenReturn(List.of(campus));
        // Act: create a building using the minimum valid code.
        Building result = service.createBuilding(new BuildingRequest(" a1 ", "Name"), 77L);
        // Confirmation: code is normalized and the building is active.
        assertEquals("A1", result.getCode());
        assertEquals(ActiveStatus.ACTIVE, result.getStatus());
        verify(buildingRepository).save(result);
    }

    @Test
    void utcid30_N_createBuilding_acceptsTwentyCharacterCode() {
        // Condition: the code has exactly twenty valid characters.
        Campus campus = new Campus(); campus.setId(3);
        when(campusRepository.findAll()).thenReturn(List.of(campus));
        // Act: create a building at the upper boundary.
        Building result = service.createBuilding(new BuildingRequest("a".repeat(20), "Name"), 77L);
        // Confirmation: normalized twenty-character code persists.
        assertEquals("A".repeat(20), result.getCode());
        verify(buildingRepository).save(result);
    }

    @Test
    void utcid31_B_createBuilding_rejectsOneCharacterCode() {
        // Condition: code is shorter than the minimum length.
        // Act: attempt to create the building.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createBuilding(new BuildingRequest("A", "Name"), 77L));
        // Confirmation: validation prevents repository writes and audit.
        assertEquals(ErrorCode.BUILDING_CODE_INVALID, ex.getCode());
        verifyNoInteractions(buildingRepository, campusRepository, auditService);
    }

    @Test
    void utcid32_B_createBuilding_rejectsTwentyOneCharacterCode() {
        // Condition: code exceeds the twenty-character maximum.
        // Act: attempt to create the building.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createBuilding(new BuildingRequest("A".repeat(21), "Name"), 77L));
        // Confirmation: code validation prevents persistence.
        assertEquals(ErrorCode.BUILDING_CODE_INVALID, ex.getCode());
        verifyNoInteractions(buildingRepository, campusRepository, auditService);
    }

    @Test
    void utcid33_B_createBuilding_rejectsBlankName() {
        // Condition: code is valid but the name is whitespace only.
        // Act: attempt to create the building.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createBuilding(new BuildingRequest("AB", "  "), 77L));
        // Confirmation: name validation occurs before uniqueness lookup.
        assertEquals(ErrorCode.LAB_NAME_INVALID, ex.getCode());
        verifyNoInteractions(buildingRepository, campusRepository, auditService);
    }

    @Test
    void utcidTv3_46_B_createBuilding_rejectsNullCode() {
        // Condition: building request does not contain a code.
        // Act: attempt to create the building.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createBuilding(new BuildingRequest(null, "Name"), 77L));
        // Confirmation: a missing code is invalid before any repository work.
        assertEquals(ErrorCode.BUILDING_CODE_INVALID, ex.getCode());
        verifyNoInteractions(buildingRepository, campusRepository);
    }

    @Test
    void utcidTv3_47_B_createBuilding_rejectsNameOver100Characters() {
        // Condition: code is valid but trimmed name is 101 characters.
        // Act: attempt to create the building.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createBuilding(
                new BuildingRequest("AB", "N".repeat(101)), 77L));
        // Confirmation: name length is checked before uniqueness lookup.
        assertEquals(ErrorCode.LAB_NAME_INVALID, ex.getCode());
        verifyNoInteractions(buildingRepository, campusRepository);
    }

    @Test
    void utcidTv3_48_N_createBuilding_acceptsNameAt100Characters() {
        // Condition: one campus exists and the name is exactly 100 characters long.
        Campus campus = new Campus(); campus.setId(3);
        when(campusRepository.findAll()).thenReturn(List.of(campus));
        // Act: create the building at the name upper boundary.
        Building result = service.createBuilding(new BuildingRequest("AB", "N".repeat(100)), 77L);
        // Confirmation: the full name is kept and the building is saved.
        assertEquals("N".repeat(100), result.getName());
        verify(buildingRepository).save(result);
    }
}
