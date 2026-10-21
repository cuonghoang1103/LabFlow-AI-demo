package vn.swt301.labflowdemo.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.swt301.labflowdemo.audit.AuditService;
import vn.swt301.labflowdemo.catalog.CalendarDtos.OperatingHoursRequest;
import vn.swt301.labflowdemo.common.BusinessException;
import vn.swt301.labflowdemo.common.ErrorCode;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test cho {@link CalendarService#setOperatingHours(OperatingHoursRequest, Long)} (sheet "setOperatingHours").
 * Một @Test = một UTCID của ma trận 5.1. Mọi dependency đều mock; "bây giờ" cố định bằng Clock.fixed.
 */
@ExtendWith(MockitoExtension.class)
class CalendarServiceSetOperatingHoursMatrixTest {

    static final Instant NOW = Instant.parse("2026-10-12T02:00:00Z");   // 09:00 thứ Hai giờ VN
    static final Long ACTOR = 9L;

    @Mock OperatingHoursRepository hoursRepository;
    @Mock BlackoutRepository blackoutRepository;
    @Mock HolidayRepository holidayRepository;
    @Mock LabRepository labRepository;
    @Mock AuditService auditService;

    CalendarService service;

    @BeforeEach
    void setUp() {
        service = new CalendarService(hoursRepository, blackoutRepository, holidayRepository, labRepository,
                auditService, ZoneId.of("Asia/Ho_Chi_Minh"), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static OperatingHoursRequest req(Integer labId, Integer day, LocalTime open, LocalTime close, boolean closed) {
        return new OperatingHoursRequest(labId, day, open, close, closed);
    }

    private static OperatingHours existingRow(Integer labId, int day, LocalTime open, LocalTime close) {
        OperatingHours h = new OperatingHours();
        h.setId(50);
        h.setLabId(labId);
        h.setDayOfWeek(day);
        h.setOpenTime(open);
        h.setCloseTime(close);
        return h;
    }

    private void assertFails(OperatingHoursRequest request, ErrorCode expected) {
        assertThatThrownBy(() -> service.setOperatingHours(request, ACTOR))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(expected);
        verify(hoursRepository, never()).save(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("UTCID01 [N] default hours (labId null), Monday 07:00-21:00, no row yet -> new row saved + audit")
    void utcid01_defaultHours_createsRow() {
        when(hoursRepository.findDefault(1)).thenReturn(Optional.empty());

        OperatingHours res = service.setOperatingHours(req(null, 1, LocalTime.of(7, 0), LocalTime.of(21, 0), false), ACTOR);

        assertThat(res.getLabId()).isNull();
        assertThat(res.getDayOfWeek()).isEqualTo(1);
        assertThat(res.isClosed()).isFalse();
        assertThat(res.getOpenTime()).isEqualTo(LocalTime.of(7, 0));
        assertThat(res.getCloseTime()).isEqualTo(LocalTime.of(21, 0));
        verify(hoursRepository).save(res);
        verify(auditService).record(ACTOR, "OPERATING_HOURS", "default/1", "SET", null, "07:00-21:00");
    }

    @Test
    @DisplayName("UTCID02 [N] lab-specific override (labId 3 exists), Wednesday 08:00-17:30 -> row saved with labId")
    void utcid02_labOverride_createsRow() {
        when(labRepository.existsById(3)).thenReturn(true);
        when(hoursRepository.findForLab(3, 3)).thenReturn(Optional.empty());

        OperatingHours res = service.setOperatingHours(req(3, 3, LocalTime.of(8, 0), LocalTime.of(17, 30), false), ACTOR);

        assertThat(res.getLabId()).isEqualTo(3);
        assertThat(res.getOpenTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(res.getCloseTime()).isEqualTo(LocalTime.of(17, 30));
        verify(hoursRepository).save(res);
        verify(hoursRepository, never()).findDefault(3);
        verify(auditService).record(ACTOR, "OPERATING_HOURS", "3/3", "SET", null, "08:00-17:30");
    }

    @Test
    @DisplayName("UTCID03 [N] row already exists -> the same row is updated (not a second one), audit keeps 'before'")
    void utcid03_existingRow_isUpdated() {
        OperatingHours old = existingRow(null, 2, LocalTime.of(7, 0), LocalTime.of(21, 0));
        when(hoursRepository.findDefault(2)).thenReturn(Optional.of(old));

        OperatingHours res = service.setOperatingHours(req(null, 2, LocalTime.of(9, 0), LocalTime.of(12, 0), false), ACTOR);

        assertThat(res).isSameAs(old);
        assertThat(res.getId()).isEqualTo(50);
        assertThat(res.getOpenTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(res.getCloseTime()).isEqualTo(LocalTime.of(12, 0));
        verify(auditService).record(ACTOR, "OPERATING_HOURS", "default/2", "SET", "07:00-21:00", "09:00-12:00");
    }

    @Test
    @DisplayName("UTCID04 [N] closed=true -> open/close are cleared (even if the request still carries times)")
    void utcid04_closedAllDay_clearsTimes() {
        OperatingHours old = existingRow(null, 7, LocalTime.of(8, 0), LocalTime.of(12, 0));
        when(hoursRepository.findDefault(7)).thenReturn(Optional.of(old));

        OperatingHours res = service.setOperatingHours(req(null, 7, LocalTime.of(8, 0), LocalTime.of(12, 0), true), ACTOR);

        assertThat(res.isClosed()).isTrue();
        assertThat(res.getOpenTime()).isNull();
        assertThat(res.getCloseTime()).isNull();
        verify(auditService).record(ACTOR, "OPERATING_HOURS", "default/7", "SET", "08:00-12:00", "closed");
    }

    @Test
    @DisplayName("UTCID05 [N] closed=true with null times (invalid hours are ignored when closed) -> saved as closed")
    void utcid05_closedWithNullTimes_isAccepted() {
        when(hoursRepository.findDefault(6)).thenReturn(Optional.empty());

        OperatingHours res = service.setOperatingHours(req(null, 6, null, null, true), ACTOR);

        assertThat(res.isClosed()).isTrue();
        verify(hoursRepository).save(res);
    }

    @Test
    @DisplayName("UTCID06 [B] dayOfWeek = 1 (lower bound, Monday) -> accepted")
    void utcid06_dayOne_isAccepted() {
        when(hoursRepository.findDefault(1)).thenReturn(Optional.empty());

        OperatingHours res = service.setOperatingHours(req(null, 1, LocalTime.of(7, 0), LocalTime.of(21, 0), false), ACTOR);

        assertThat(res.getDayOfWeek()).isEqualTo(1);
    }

    @Test
    @DisplayName("UTCID07 [B] dayOfWeek = 7 (upper bound, Sunday) -> accepted")
    void utcid07_daySeven_isAccepted() {
        when(hoursRepository.findDefault(7)).thenReturn(Optional.empty());

        OperatingHours res = service.setOperatingHours(req(null, 7, LocalTime.of(7, 0), LocalTime.of(21, 0), false), ACTOR);

        assertThat(res.getDayOfWeek()).isEqualTo(7);
    }

    @Test
    @DisplayName("UTCID08 [B] dayOfWeek = 0 (just below range) -> CALENDAR_DAY_INVALID")
    void utcid08_dayZero_throwsDayInvalid() {
        assertFails(req(null, 0, LocalTime.of(7, 0), LocalTime.of(21, 0), false), ErrorCode.CALENDAR_DAY_INVALID);
    }

    @Test
    @DisplayName("UTCID09 [B] dayOfWeek = 8 (just above range) -> CALENDAR_DAY_INVALID")
    void utcid09_dayEight_throwsDayInvalid() {
        assertFails(req(null, 8, LocalTime.of(7, 0), LocalTime.of(21, 0), false), ErrorCode.CALENDAR_DAY_INVALID);
    }

    @Test
    @DisplayName("UTCID10 [A] dayOfWeek = null -> CALENDAR_DAY_INVALID")
    void utcid10_dayNull_throwsDayInvalid() {
        assertFails(req(null, null, LocalTime.of(7, 0), LocalTime.of(21, 0), false), ErrorCode.CALENDAR_DAY_INVALID);
    }

    @Test
    @DisplayName("UTCID11 [A] closed=true but dayOfWeek = 9 -> day is still validated -> CALENDAR_DAY_INVALID")
    void utcid11_closedButBadDay_throwsDayInvalid() {
        assertFails(req(null, 9, null, null, true), ErrorCode.CALENDAR_DAY_INVALID);
    }

    @Test
    @DisplayName("UTCID12 [A] open time = null (closed=false) -> CALENDAR_HOURS_INVALID")
    void utcid12_openNull_throwsHoursInvalid() {
        assertFails(req(null, 1, null, LocalTime.of(21, 0), false), ErrorCode.CALENDAR_HOURS_INVALID);
    }

    @Test
    @DisplayName("UTCID13 [A] close time = null (closed=false) -> CALENDAR_HOURS_INVALID")
    void utcid13_closeNull_throwsHoursInvalid() {
        assertFails(req(null, 1, LocalTime.of(7, 0), null, false), ErrorCode.CALENDAR_HOURS_INVALID);
    }

    @Test
    @DisplayName("UTCID14 [B] open == close (09:00-09:00) -> CALENDAR_HOURS_INVALID")
    void utcid14_openEqualsClose_throwsHoursInvalid() {
        assertFails(req(null, 1, LocalTime.of(9, 0), LocalTime.of(9, 0), false), ErrorCode.CALENDAR_HOURS_INVALID);
    }

    @Test
    @DisplayName("UTCID15 [A] open after close (21:00-07:00) -> CALENDAR_HOURS_INVALID")
    void utcid15_openAfterClose_throwsHoursInvalid() {
        assertFails(req(null, 1, LocalTime.of(21, 0), LocalTime.of(7, 0), false), ErrorCode.CALENDAR_HOURS_INVALID);
    }

    @Test
    @DisplayName("UTCID16 [B] open 07:15 (not on a 30-minute mark) -> CALENDAR_HOURS_INVALID")
    void utcid16_openOffGrid_throwsHoursInvalid() {
        assertFails(req(null, 1, LocalTime.of(7, 15), LocalTime.of(21, 0), false), ErrorCode.CALENDAR_HOURS_INVALID);
    }

    @Test
    @DisplayName("UTCID17 [B] close 21:10 (not on a 30-minute mark) -> CALENDAR_HOURS_INVALID")
    void utcid17_closeOffGrid_throwsHoursInvalid() {
        assertFails(req(null, 1, LocalTime.of(7, 0), LocalTime.of(21, 10), false), ErrorCode.CALENDAR_HOURS_INVALID);
    }

    @Test
    @DisplayName("UTCID18 [B] open 07:00:30 (minute ok but seconds != 0) -> CALENDAR_HOURS_INVALID")
    void utcid18_openWithSeconds_throwsHoursInvalid() {
        assertFails(req(null, 1, LocalTime.of(7, 0, 30), LocalTime.of(21, 0), false), ErrorCode.CALENDAR_HOURS_INVALID);
    }

    @Test
    @DisplayName("UTCID19 [B] shortest valid range 08:00-08:30 on the 30-minute marks -> accepted")
    void utcid19_shortestRange_isAccepted() {
        when(hoursRepository.findDefault(4)).thenReturn(Optional.empty());

        OperatingHours res = service.setOperatingHours(req(null, 4, LocalTime.of(8, 0), LocalTime.of(8, 30), false), ACTOR);

        assertThat(res.getOpenTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(res.getCloseTime()).isEqualTo(LocalTime.of(8, 30));
    }

    @Test
    @DisplayName("UTCID20 [A] labId 99 does not exist -> LAB_NOT_FOUND, nothing saved")
    void utcid20_labNotFound_throwsLabNotFound() {
        when(labRepository.existsById(99)).thenReturn(false);

        assertFails(req(99, 1, LocalTime.of(7, 0), LocalTime.of(21, 0), false), ErrorCode.LAB_NOT_FOUND);
    }

    @Test
    @DisplayName("UTCID21 [A] closed=true but labId 99 does not exist -> LAB_NOT_FOUND")
    void utcid21_closedButLabNotFound_throwsLabNotFound() {
        when(labRepository.existsById(99)).thenReturn(false);

        assertFails(req(99, 1, null, null, true), ErrorCode.LAB_NOT_FOUND);
    }

    @Test
    @DisplayName("UTCID22 [A] bad hours AND missing lab -> hours are checked first -> CALENDAR_HOURS_INVALID")
    void utcid22_badHoursBeforeLabCheck_throwsHoursInvalid() {
        assertFails(req(99, 1, LocalTime.of(21, 0), LocalTime.of(7, 0), false), ErrorCode.CALENDAR_HOURS_INVALID);
        verify(labRepository, never()).existsById(eq(99));
        verify(auditService, never()).record(any(), any(), any(), any(), isNull(), any());
    }
}
