package vn.swt301.labflowdemo.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalTime;

/** Request shapes for the operating calendar (S18). */
public final class CalendarDtos {

    private CalendarDtos() {
    }

    public record OperatingHoursRequest(Integer labId, @NotNull Integer dayOfWeek, LocalTime openTime,
                                        LocalTime closeTime, boolean closed) {
    }

    public record BlackoutRequest(@NotNull Integer labId, @NotNull Instant startAt, @NotNull Instant endAt,
                                  @NotBlank @Size(max = 255) String reason) {
    }
}
