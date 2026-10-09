package vn.swt301.labflowdemo.catalog;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalTime;
/** Opening hours for one weekday. labId null = default for all labs; a lab row overrides it. */
@Entity
@Table(name = "operating_hours")
@Getter
@Setter
@NoArgsConstructor
public class OperatingHours {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private Integer labId;
    /** 1 = Monday ... 7 = Sunday (ISO-8601, same as java.time.DayOfWeek). */
    private int dayOfWeek;
    private LocalTime openTime;
    private LocalTime closeTime;
    private boolean closed;
}
