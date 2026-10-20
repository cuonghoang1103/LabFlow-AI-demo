package vn.swt301.labflowdemo.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.swt301.labflowdemo.common.BusinessException;
import vn.swt301.labflowdemo.common.ErrorCode;
import vn.swt301.labflowdemo.settings.SettingsService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingPolicyTest {
    static final Instant NOW = Instant.parse("2026-10-12T02:00:00Z");
    @Mock SettingsService settings; BookingPolicy policy;
    @BeforeEach void setUp() { policy = new BookingPolicy(settings, ZoneId.of("Asia/Ho_Chi_Minh"), Clock.fixed(NOW, ZoneOffset.UTC)); lenient().when(settings.getInt("booking.slot-minutes")).thenReturn(30); lenient().when(settings.getInt("booking.max-advance-days")).thenReturn(14); }

    @Test void utcid18_checkPeriod_validFutureGrid_passes() {
        // Arrange - Condition: future period on 30-minute grid and within advance limit
        // Act + Assert - Confirmation: no exception
        policy.checkPeriod(Instant.parse("2026-10-12T03:00:00Z"), Instant.parse("2026-10-12T04:00:00Z"));
    }
    @Test void utcid19_checkPeriod_nullOrReversed_throwsRangeInvalid() {
        // Arrange - Condition: null endpoint makes an invalid range
        // Act + Assert - Confirmation
        assertThatThrownBy(() -> policy.checkPeriod(null, NOW)).isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException)e).getCode()).isEqualTo(ErrorCode.TIME_RANGE_INVALID);
    }
    @Test void utcid20_checkPeriod_offGrid_throwsMisaligned() {
        // Arrange - Condition: start has minute 15, not the configured 30-minute grid
        // Act + Assert - Confirmation
        assertThatThrownBy(() -> policy.checkPeriod(Instant.parse("2026-10-12T03:15:00Z"), Instant.parse("2026-10-12T04:00:00Z"))).isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException)e).getCode()).isEqualTo(ErrorCode.TIME_SLOT_MISALIGNED);
    }
    @Test void utcid21_checkPeriod_past_throwsInPast() {
        // Arrange - Condition: aligned start is not after current time
        // Act + Assert - Confirmation
        assertThatThrownBy(() -> policy.checkPeriod(Instant.parse("2026-10-12T01:00:00Z"), Instant.parse("2026-10-12T02:00:00Z"))).isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException)e).getCode()).isEqualTo(ErrorCode.TIME_IN_PAST);
    }
    @Test void utcid22_checkPeriod_tooFarAhead_throwsTooFar() {
        // Arrange - Condition: aligned start exceeds configured 14-day limit
        // Act + Assert - Confirmation
        assertThatThrownBy(() -> policy.checkPeriod(Instant.parse("2026-10-27T03:00:00Z"), Instant.parse("2026-10-27T04:00:00Z"))).isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException)e).getCode()).isEqualTo(ErrorCode.TIME_TOO_FAR_AHEAD);
    }

    @Test void checkPeriod_exactlyFourteenDaysAhead_isAllowed() {
        // Condition: start is on the half-hour grid exactly 14 days after NOW.
        // Act and confirmation: the inclusive upper advance boundary is accepted.
        policy.checkPeriod(NOW.plusSeconds(14L * 24 * 60 * 60),
                NOW.plusSeconds(14L * 24 * 60 * 60 + 1800));
    }

    @Test void checkPeriod_startEqualToNow_isRejected() {
        // Condition: start equals the fixed current time (not strictly in the future).
        // Act and confirmation: reject even though the time is aligned to the grid.
        assertThatThrownBy(() -> policy.checkPeriod(NOW, NOW.plusSeconds(1800)))
                .isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.TIME_IN_PAST);
    }

    @Test void checkPeriod_equalEndpoints_isRejectedBeforeCheckingGrid() {
        // Condition: a zero-length time period.
        // Act and confirmation: range validation takes precedence over alignment.
        assertThatThrownBy(() -> policy.checkPeriod(NOW.plusSeconds(3600), NOW.plusSeconds(3600)))
                .isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.TIME_RANGE_INVALID);
    }

    @Test void checkPeriod_endOffGrid_isRejected() {
        // Condition: start is on grid, but the end falls 15 minutes off grid.
        // Act and confirmation: both endpoints must align, not only the start.
        assertThatThrownBy(() -> policy.checkPeriod(NOW.plusSeconds(3600), NOW.plusSeconds(4500)))
                .isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.TIME_SLOT_MISALIGNED);
    }
}
