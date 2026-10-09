package vn.swt301.labflowdemo.settings;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.swt301.labflowdemo.audit.AuditService;
import vn.swt301.labflowdemo.common.BusinessException;
import vn.swt301.labflowdemo.common.ErrorCode;
import vn.swt301.labflowdemo.common.Texts;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Reads and changes the configurable business rules (BR-01..BR-12 values). Screen S19, owner C5.
 * Các service khác đọc luật qua đây thay vì viết cứng con số trong code.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService {

    private final AppSettingRepository repository;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<AppSetting> listSettings() {
        return repository.findAll();
    }

    /** @return the integer value of a setting; fails loudly when the key is unknown. */
    @Transactional(readOnly = true)
    public int getInt(String key) {
        return Integer.parseInt(find(key).getValue());
    }

    /** @return a comma separated setting as a trimmed lower-case list. */
    @Transactional(readOnly = true)
    public List<String> getList(String key) {
        return Arrays.stream(find(key).getValue().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(String::toLowerCase).toList();
    }

    /**
     * Changes one setting after checking its type and allowed range.
     *
     * @param key     setting key, e.g. {@code booking.max-advance-days}
     * @param rawValue new value as text
     * @param actorId admin who changes it (written to the audit log)
     * @return the updated setting
     * @throws BusinessException SETTING_NOT_FOUND, SETTING_VALUE_INVALID
     */
    @Transactional
    public AppSetting updateSetting(String key, String rawValue, Long actorId) {
        AppSetting setting = find(key);
        String value = Texts.clean(rawValue);
        if (value == null || value.length() > 255) {
            throw new BusinessException(ErrorCode.SETTING_VALUE_INVALID);
        }
        switch (setting.getValueType()) {
            case "INT" -> {
                int number;
                try {
                    number = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCode.SETTING_VALUE_INVALID, "Value must be a whole number");
                }
                // min/max có thể null = không giới hạn phía đó
                if ((setting.getMinValue() != null && number < setting.getMinValue())
                        || (setting.getMaxValue() != null && number > setting.getMaxValue())) {
                    throw new BusinessException(ErrorCode.SETTING_VALUE_INVALID,
                            "Value must be between " + setting.getMinValue() + " and " + setting.getMaxValue());
                }
                value = String.valueOf(number);
            }
            case "BOOL" -> {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    throw new BusinessException(ErrorCode.SETTING_VALUE_INVALID, "Value must be true or false");
                }
                value = value.toLowerCase();
            }
            default -> { /* STRING: any 1-255 characters */ }
        }
        String before = setting.getValue();
        setting.setValue(value);
        setting.setUpdatedAt(Instant.now(clock));
        setting.setUpdatedBy(actorId);
        auditService.record(actorId, "SETTING", key, "UPDATE", before, value);
        log.info("Setting updated: key={}, from={}, to={}, by={}", key, before, value, actorId);
        return setting;
    }

    private AppSetting find(String key) {
        return repository.findById(key).orElseThrow(() -> new BusinessException(ErrorCode.SETTING_NOT_FOUND));
    }
}
