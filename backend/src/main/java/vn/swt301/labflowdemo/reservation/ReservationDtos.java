package vn.swt301.labflowdemo.reservation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class ReservationDtos {

    private ReservationDtos() {
    }

    public record CreateReservationRequest(@NotNull Integer labId, @NotNull Instant startAt, @NotNull Instant endAt,
                                           @NotBlank @Size(max = 255) String purpose) {
    }

    public record ReservationView(Long id, Long userId, Integer labId, String status, Instant startAt, Instant endAt,
                                  String purpose, boolean replayed) {
    }
}
