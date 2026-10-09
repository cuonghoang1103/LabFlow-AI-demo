package vn.swt301.labflowdemo.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByIdempotencyKey(String idempotencyKey);

    List<Reservation> findByUserIdOrderByStartAtDesc(Long userId);
}
