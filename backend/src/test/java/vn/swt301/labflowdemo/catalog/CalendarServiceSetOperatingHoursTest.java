package vn.swt301.labflowdemo.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.swt301.labflowdemo.audit.AuditService;
import vn.swt301.labflowdemo.catalog.CalendarDtos.OperatingHoursRequest;
import vn.swt301.labflowdemo.common.BusinessException;
import vn.swt301.labflowdemo.common.ErrorCode;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarServiceSetOperatingHoursTest {

    private static final Instant NOW = Instant.parse("2026-10-12T02:00:00Z");
    private static final Long ACTOR = 9L;
    private static final int MONDAY = 1;

    @Mock OperatingHoursRepository hoursRepository;
    @Mock BlackoutRepository blackoutRepository;
    @Mock HolidayRepository holidayRepository;
    @Mock LabRepository labRepository;
    @Mock AuditService auditService;

    private CalendarService service;

    @BeforeEach
    void setUp() {
        service = new CalendarService(hoursRepository, blackoutRepository, holidayRepository, labRepository,
                auditService, ZoneOffset.UTC, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static OperatingHoursRequest request(Integer labId, Integer day, LocalTime open, LocalTime close,
                                                  boolean closed) {
        return new OperatingHoursRequest(labId, day, open, close, closed);
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
    @DisplayName("valid default schedule is saved and audited")
    void validDefaultHours_areSaved() {
        when(hoursRepository.findDefault(MONDAY)).thenReturn(Optional.empty());

        OperatingHours result = service.setOperatingHours(
                request(null, MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 30), false), ACTOR);

        assertThat(result.getLabId()).isNull();
        assertThat(result.getDayOfWeek()).isEqualTo(MONDAY);
        assertThat(result.getOpenTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(result.getCloseTime()).isEqualTo(LocalTime.of(17, 30));
        assertThat(result.isClosed()).isFalse();
        verify(hoursRepository).save(result);
        verify(auditService).record(ACTOR, "OPERATING_HOURS", "default/1", "SET", null, "08:00-17:30");
    }

    @Test
    @DisplayName("existing lab schedule is updated and old/new values are audited")
    void existingLabHours_areUpdated() {
        OperatingHours existing = new OperatingHours();
        existing.setLabId(7);
        existing.setDayOfWeek(MONDAY);
        existing.setOpenTime(LocalTime.of(9, 0));
        existing.setCloseTime(LocalTime.of(17, 0));
        when(labRepository.existsById(7)).thenReturn(true);
        when(hoursRepository.findForLab(7, MONDAY)).thenReturn(Optional.of(existing));

        OperatingHours result = service.setOperatingHours(
                request(7, MONDAY, LocalTime.of(8, 30), LocalTime.of(18, 0), false), ACTOR);

        assertThat(result).isSameAs(existing);
        verify(hoursRepository).save(existing);
        verify(auditService).record(ACTOR, "OPERATING_HOURS", "7/1", "SET", "09:00-17:00", "08:30-18:00");
    }

    @Test
    @DisplayName("closed day accepts null times and clears any prior opening times")
    void closedDay_clearsTimes() {
        OperatingHours existing = new OperatingHours();
        existing.setOpenTime(LocalTime.of(8, 0));
        existing.setCloseTime(LocalTime.of(17, 0));
        when(hoursRepository.findDefault(MONDAY)).thenReturn(Optional.of(existing));

        OperatingHours result = service.setOperatingHours(request(null, MONDAY, null, null, true), ACTOR);

        assertThat(result.isClosed()).isTrue();
        assertThat(result.getOpenTime()).isNull();
        assertThat(result.getCloseTime()).isNull();
        verify(auditService).record(ACTOR, "OPERATING_HOURS", "default/1", "SET", "08:00-17:00", "closed");
    }

    @Test
    @DisplayName("day outside ISO weekday range is rejected")
    void invalidDay_throwsCalendarDayInvalid() {
        assertFails(request(null, 8, LocalTime.of(8, 0), LocalTime.of(17, 0), false),
                ErrorCode.CALENDAR_DAY_INVALID);
    }

    @Test
    @DisplayName("missing opening/closing time is rejected for an open day")
    void missingTime_throwsCalendarHoursInvalid() {
        assertFails(request(null, MONDAY, null, LocalTime.of(17, 0), false), ErrorCode.CALENDAR_HOURS_INVALID);
    }

    @Test
    @DisplayName("opening time must be before closing time")
    void reversedRange_throwsCalendarHoursInvalid() {
        assertFails(request(null, MONDAY, LocalTime.of(17, 0), LocalTime.of(8, 0), false),
                ErrorCode.CALENDAR_HOURS_INVALID);
    }

    @Test
    @DisplayName("times must be on a 30-minute mark")
    void offGridTime_throwsCalendarHoursInvalid() {
        assertFails(request(null, MONDAY, LocalTime.of(8, 15), LocalTime.of(17, 0), false),
                ErrorCode.CALENDAR_HOURS_INVALID);
    }

    @Test
    @DisplayName("unknown lab override is rejected")
    void unknownLab_throwsLabNotFound() {
        when(labRepository.existsById(7)).thenReturn(false);

        assertFails(request(7, MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0), false),
                ErrorCode.LAB_NOT_FOUND);
        verify(hoursRepository, never()).findForLab(7, MONDAY);
    }
}
