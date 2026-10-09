package vn.swt301.labflowdemo.reservation;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** What a reservation holds (a lab OR one equipment unit). The period is copied so ONE table carries the constraint. */
@Entity
@Table(name = "reservation_items")
@Getter
@Setter
@NoArgsConstructor
public class ReservationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long reservationId;
    private Integer labId;
    private Long equipmentId;
    private Instant startAt;
    private Instant endAt;
    /** false when the reservation is cancelled / no-show: the slot is free again. */
    private boolean active;
}
