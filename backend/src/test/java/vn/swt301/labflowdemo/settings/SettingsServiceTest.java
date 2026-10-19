package vn.swt301.labflowdemo.settings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.swt301.labflowdemo.audit.AuditService;
import vn.swt301.labflowdemo.common.BusinessException;
import vn.swt301.labflowdemo.common.ErrorCode;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {
    static final Instant NOW = Instant.parse("2026-10-12T02:00:00Z");
    @Mock AppSettingRepository repository; @Mock AuditService audit;
    SettingsService service;
    @BeforeEach void setUp() { service = new SettingsService(repository, audit, Clock.fixed(NOW, ZoneOffset.UTC)); }
    private AppSetting setting(String type, String value, Integer min, Integer max) { AppSetting s = new AppSetting(); s.setKey("booking.max-advance-days"); s.setValueType(type); s.setValue(value); s.setMinValue(min); s.setMaxValue(max); return s; }

    @Test void utcid14_updateSetting_validInt_normalizesAndAudits() {
        // Arrange - Condition: INT setting with value in allowed range
        AppSetting s = setting("INT", "14", 1, 30); when(repository.findById(s.getKey())).thenReturn(Optional.of(s));
        // Act
        AppSetting result = service.updateSetting(s.getKey(), " 20 ", 9L);
        // Assert - Confirmation
        assertThat(result.getValue()).isEqualTo("20"); assertThat(result.getUpdatedAt()).isEqualTo(NOW); assertThat(result.getUpdatedBy()).isEqualTo(9L); verify(audit).record(9L, "SETTING", s.getKey(), "UPDATE", "14", "20");
    }
    @Test void utcid15_updateSetting_outOfRange_throwsInvalid() {
        // Arrange - Condition: INT value exceeds configured maximum
        AppSetting s = setting("INT", "14", 1, 30); when(repository.findById(s.getKey())).thenReturn(Optional.of(s));
        // Act + Assert - Confirmation
        assertThatThrownBy(() -> service.updateSetting(s.getKey(), "31", 9L)).isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException)e).getCode()).isEqualTo(ErrorCode.SETTING_VALUE_INVALID);
    }
    @Test void utcid16_updateSetting_invalidBool_throwsInvalid() {
        // Arrange - Condition: BOOL value is neither true nor false
        AppSetting s = setting("BOOL", "true", null, null); when(repository.findById(s.getKey())).thenReturn(Optional.of(s));
        // Act + Assert - Confirmation
        assertThatThrownBy(() -> service.updateSetting(s.getKey(), "maybe", 9L)).isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException)e).getCode()).isEqualTo(ErrorCode.SETTING_VALUE_INVALID);
    }
    @Test void utcid17_updateSetting_unknownKey_throwsNotFound() {
        // Arrange - Condition: repository has no setting
        when(repository.findById("missing")).thenReturn(Optional.empty());
        // Act + Assert - Confirmation
        assertThatThrownBy(() -> service.updateSetting("missing", "1", 9L)).isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException)e).getCode()).isEqualTo(ErrorCode.SETTING_NOT_FOUND);
    }
}
