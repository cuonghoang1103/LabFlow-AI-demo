package vn.swt301.labflowdemo.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.swt301.labflowdemo.common.BusinessException;
import vn.swt301.labflowdemo.common.ErrorCode;
import vn.swt301.labflowdemo.settings.SettingsService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Time rules every requested period must follow, shared by availability and reservation:
 * start &lt; end, on the slot grid (BR-04, 30 minutes), in the future, at most N days ahead (BR-05, 14).
 */
@Service
@RequiredArgsConstructor
public class BookingPolicy {

    private final SettingsService settings;
    private final ZoneId businessZone;
    private final Clock clock;

    /**
     * @throws BusinessException TIME_RANGE_INVALID, TIME_SLOT_MISALIGNED, TIME_IN_PAST, TIME_TOO_FAR_AHEAD
     */
    public void checkPeriod(Instant start, Instant end) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new BusinessException(ErrorCode.TIME_RANGE_INVALID);
        }
        int slot = settings.getInt("booking.slot-minutes");
        if (!onGrid(start, slot) || !onGrid(end, slot)) {
            throw new BusinessException(ErrorCode.TIME_SLOT_MISALIGNED);
        }
        Instant now = Instant.now(clock);
        if (!start.isAfter(now)) {
            throw new BusinessException(ErrorCode.TIME_IN_PAST);
        }
        // BR-05: bắt đầu chậm nhất N ngày kể từ bây giờ (đúng N ngày vẫn được)
        if (start.isAfter(now.plus(Duration.ofDays(settings.getInt("booking.max-advance-days"))))) {
            throw new BusinessException(ErrorCode.TIME_TOO_FAR_AHEAD);
        }
    }

    private boolean onGrid(Instant t, int slotMinutes) {
        ZonedDateTime local = t.atZone(businessZone);
        return local.getMinute() % slotMinutes == 0 && local.getSecond() == 0 && local.getNano() == 0;
    }
}
