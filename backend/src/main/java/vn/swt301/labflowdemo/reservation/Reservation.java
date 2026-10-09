package vn.swt301.labflowdemo.reservation;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "reservations")
@Getter
@Setter
@NoArgsConstructor
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    /** Half-open period [startAt, endAt), stored in UTC. */
    private Instant startAt;
    private Instant endAt;
    private String purpose;
    /** Same key sent twice (double click, retry) = same reservation, not a second one. */
    private String idempotencyKey;
    private Instant createdAt;

    @Version
    private int version;
}
