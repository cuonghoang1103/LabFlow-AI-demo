package vn.swt301.labflowdemo.catalog;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
/** A period when a lab cannot be booked (maintenance, event). Half-open [startAt, endAt). */
@Entity
@Table(name = "blackouts")
@Getter
@Setter
@NoArgsConstructor
public class Blackout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Integer labId;
    private Instant startAt;
    private Instant endAt;
    private String reason;
    private Long createdBy;
}
