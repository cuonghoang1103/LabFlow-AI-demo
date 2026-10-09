package vn.swt301.labflowdemo.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One configurable business rule (table app_settings, screen S19). */
@Entity
@Table(name = "app_settings")
@Getter
@Setter
@NoArgsConstructor
public class AppSetting {

    @Id
    @Column(name = "setting_key")
    private String key;

    @Column(name = "setting_value", nullable = false)
    private String value;

    /** INT, STRING or BOOL. */
    @Column(name = "value_type", nullable = false)
    private String valueType;

    private Integer minValue;
    private Integer maxValue;
    private String description;
    private Instant updatedAt;
    private Long updatedBy;
}
