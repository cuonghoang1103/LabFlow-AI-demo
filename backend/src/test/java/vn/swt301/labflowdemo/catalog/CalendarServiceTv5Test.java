package vn.swt301.labflowdemo.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.swt301.labflowdemo.audit.AuditService;
import vn.swt301.labflowdemo.catalog.CalendarDtos.BlackoutRequest;
import vn.swt301.labflowdemo.catalog.CalendarDtos.OperatingHoursRequest;
import vn.swt301.labflowdemo.common.BusinessException;
import vn.swt301.labflowdemo.common.ErrorCode;

import java.time.*;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalendarServiceTv5Test {
    @Mock OperatingHoursRepository hoursRepository;
    @Mock BlackoutRepository blackoutRepository;
    @Mock HolidayRepository holidayRepository;
    @Mock LabRepository labRepository;
    @Mock AuditService auditService;
    private CalendarService service;
    private final ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), zone);

    @BeforeEach
    void setUp() { service = new CalendarService(hoursRepository, blackoutRepository, holidayRepository, labRepository, auditService, zone, clock); }

    @Test
    void utcid61_N_setOperatingHours_savesHalfHourWindow() {
        // Arrange: default Monday has no existing row.
        when(hoursRepository.findDefault(1)).thenReturn(Optional.empty());
        // Condition: set a valid 08:00-17:00 operating window.
        OperatingHours result = service.setOperatingHours(new OperatingHoursRequest(null, 1, LocalTime.of(8, 0), LocalTime.of(17, 0), false), 9L);
        // Confirmation: times and weekday are stored and audited.
        assertAll(() -> assertEquals(1, result.getDayOfWeek()), () -> assertEquals(LocalTime.of(8, 0), result.getOpenTime()),
                () -> assertEquals(LocalTime.of(17, 0), result.getCloseTime()), () -> assertFalse(result.isClosed()));
        verify(hoursRepository).save(result);
        verify(auditService).record(9L, "OPERATING_HOURS", "default/1", "SET", null, "08:00-17:00");
    }

    @Test
    void utcid62_B_setOperatingHours_rejectsNonHalfHourWithErrorCode() {
        // Arrange: opening time is not aligned to the 30-minute grid.
        // Condition: attempt to set invalid calendar hours.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.setOperatingHours(
                new OperatingHoursRequest(null, 1, LocalTime.of(8, 15), LocalTime.of(17, 0), false), 9L));
        // Confirmation: CALENDAR_HOURS_INVALID is returned without persistence.
        assertEquals(ErrorCode.CALENDAR_HOURS_INVALID, ex.getCode());
        verifyNoInteractions(hoursRepository, auditService);
    }

    @Test
    void utcid63_N_addBlackout_savesFutureNonOverlappingRange() {
        // Arrange: lab exists, range is future, and no blackout overlaps.
        Instant start = Instant.parse("2026-10-20T02:00:00Z");
        Instant end = Instant.parse("2026-10-20T04:00:00Z");
        when(labRepository.existsById(10)).thenReturn(true);
        when(blackoutRepository.existsOverlap(10, start, end)).thenReturn(false);
        doAnswer(inv -> { ((Blackout) inv.getArgument(0)).setId(41L); return inv.getArgument(0); }).when(blackoutRepository).save(any());
        // Condition: add a maintenance blackout with a valid reason.
        Blackout result = service.addBlackout(new BlackoutRequest(10, start, end, "Maintenance"), 9L);
        // Confirmation: all blackout fields are saved and audit receives the created object.
        assertAll(() -> assertEquals(10, result.getLabId()), () -> assertEquals(start, result.getStartAt()),
                () -> assertEquals("Maintenance", result.getReason()), () -> assertEquals(9L, result.getCreatedBy()));
        verify(blackoutRepository).save(result);
    }

    @Test
    void utcid64_A_addBlackout_rejectsOverlapWithErrorCode() {
        // Arrange: lab exists but another blackout covers part of the requested period.
        Instant start = Instant.parse("2026-10-20T02:00:00Z");
        Instant end = Instant.parse("2026-10-20T04:00:00Z");
        when(labRepository.existsById(10)).thenReturn(true);
        when(blackoutRepository.existsOverlap(10, start, end)).thenReturn(true);
        // Condition: attempt to add an overlapping blackout.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.addBlackout(new BlackoutRequest(10, start, end, "Event"), 9L));
        // Confirmation: BLACKOUT_OVERLAP prevents duplicate unavailability.
        assertEquals(ErrorCode.BLACKOUT_OVERLAP, ex.getCode());
        verify(blackoutRepository, never()).save(any());
    }

    @Test
    void utcid65_N_checkLabOpen_acceptsWholePeriodInsideHours() {
        // Arrange: Monday default hours are 08:00-17:00 local and no holiday/blackout applies.
        OperatingHours hours = new OperatingHours(); hours.setDayOfWeek(1); hours.setOpenTime(LocalTime.of(8, 0)); hours.setCloseTime(LocalTime.of(17, 0));
        when(holidayRepository.existsById(LocalDate.of(2026, 10, 19))).thenReturn(false);
        when(hoursRepository.findForLab(10, 1)).thenReturn(Optional.empty());
        when(hoursRepository.findDefault(1)).thenReturn(Optional.of(hours));
        when(blackoutRepository.existsOverlap(eq(10), any(), any())).thenReturn(false);
        // Condition: check 09:00-10:00 Vietnam time.
        service.checkLabOpen(10, Instant.parse("2026-10-19T02:00:00Z"), Instant.parse("2026-10-19T03:00:00Z"));
        // Confirmation: no exception means the complete period is open.
        verify(blackoutRepository).existsOverlap(10, Instant.parse("2026-10-19T02:00:00Z"), Instant.parse("2026-10-19T03:00:00Z"));
    }

    @Test
    void utcid66_B_checkLabOpen_rejectsBlackoutWithErrorCode() {
        // Arrange: hours are open but the requested period overlaps a blackout.
        OperatingHours hours = new OperatingHours(); hours.setOpenTime(LocalTime.of(8, 0)); hours.setCloseTime(LocalTime.of(17, 0));
        when(holidayRepository.existsById(LocalDate.of(2026, 10, 19))).thenReturn(false);
        when(hoursRepository.findForLab(10, 1)).thenReturn(Optional.empty());
        when(hoursRepository.findDefault(1)).thenReturn(Optional.of(hours));
        when(blackoutRepository.existsOverlap(eq(10), any(), any())).thenReturn(true);
        // Condition: check a period covered by the blackout.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.checkLabOpen(10, Instant.parse("2026-10-19T02:00:00Z"), Instant.parse("2026-10-19T03:00:00Z")));
        // Confirmation: TIME_BLACKOUT distinguishes unavailability from closed hours.
        assertEquals(ErrorCode.TIME_BLACKOUT, ex.getCode());
    }

    @Test
    void setOperatingHours_closedDayIgnoresTimes() {
        // Given: closed=true means opening and closing times are irrelevant even when null.
        when(hoursRepository.findDefault(7)).thenReturn(Optional.empty());
        // When: Sunday is marked closed.
        OperatingHours result = service.setOperatingHours(new OperatingHoursRequest(null, 7, null, null, true), 9L);
        // Then: the persisted row contains no hours and is explicitly closed.
        assertTrue(result.isClosed()); assertNull(result.getOpenTime()); assertNull(result.getCloseTime());
        verify(hoursRepository).save(result);
    }

    @Test
    void setOperatingHours_invalidWeekdayDoesNotPersist() {
        // Given: 8 is outside the valid ISO weekday range 1..7.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.setOperatingHours(
                new OperatingHoursRequest(null, 8, LocalTime.of(8, 0), LocalTime.of(17, 0), false), 9L));
        // Then: reject before querying/saving any calendar row.
        assertEquals(ErrorCode.CALENDAR_DAY_INVALID, ex.getCode());
        verifyNoInteractions(hoursRepository, auditService);
    }

    @Test
    void addBlackout_missingLabDoesNotSave() {
        // Given: requested lab does not exist.
        when(labRepository.existsById(10)).thenReturn(false);
        // When: attempting to create a blackout.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.addBlackout(new BlackoutRequest(10,
                Instant.parse("2026-10-20T02:00:00Z"), Instant.parse("2026-10-20T04:00:00Z"), "Maintenance"), 9L));
        // Then: report LAB_NOT_FOUND and never save an orphan blackout.
        assertEquals(ErrorCode.LAB_NOT_FOUND, ex.getCode());
        verifyNoInteractions(blackoutRepository, auditService);
    }

    @Test
    void addBlackout_endAtNow_isInvalid() {
        // Given: the blackout end equals NOW (it is not in the future).
        when(labRepository.existsById(10)).thenReturn(true);
        Instant now = clock.instant();
        BusinessException ex = assertThrows(BusinessException.class, () -> service.addBlackout(
                new BlackoutRequest(10, now.minusSeconds(3600), now, "Maintenance"), 9L));
        // Then: reject at the time boundary before overlap checking and persistence.
        assertEquals(ErrorCode.BLACKOUT_RANGE_INVALID, ex.getCode());
        verifyNoInteractions(blackoutRepository, auditService);
    }

    @Test
    void checkLabOpen_holidayBlocksEvenWithinNormalHours() {
        // Given: the whole time window falls on a holiday in Vietnam.
        when(holidayRepository.existsById(LocalDate.of(2026, 10, 19))).thenReturn(true);
        // When: checking an otherwise valid 09:00-10:00 period.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.checkLabOpen(10,
                Instant.parse("2026-10-19T02:00:00Z"), Instant.parse("2026-10-19T03:00:00Z")));
        // Then: reject before reading hours or blackouts.
        assertEquals(ErrorCode.TIME_OUTSIDE_OPENING_HOURS, ex.getCode());
        verifyNoInteractions(hoursRepository, blackoutRepository);
    }

    @Test
    void checkLabOpen_exactOpeningAndClosingTimesAreAllowed() {
        // Given: an open Monday, 07:00-21:00 local, with no blackout.
        OperatingHours hours = new OperatingHours(); hours.setOpenTime(LocalTime.of(7, 0)); hours.setCloseTime(LocalTime.of(21, 0));
        when(hoursRepository.findForLab(10, 1)).thenReturn(Optional.of(hours));
        Instant start = Instant.parse("2026-10-19T00:00:00Z"); // 07:00 in Vietnam
        Instant end = Instant.parse("2026-10-19T14:00:00Z"); // 21:00 in Vietnam
        // When/then: both endpoints exactly on the limits must remain valid.
        assertDoesNotThrow(() -> service.checkLabOpen(10, start, end));
        verify(blackoutRepository).existsOverlap(10, start, end);
    }

    @Test void setOperatingHours_missingLabDoesNotSave() {
        // Condition: lab-specific hours refer to a lab that does not exist.
        when(labRepository.existsById(10)).thenReturn(false);
        // Act: set an otherwise valid operating window.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.setOperatingHours(
                new OperatingHoursRequest(10, 1, LocalTime.of(8, 0), LocalTime.of(17, 0), false), 9L));
        // Confirmation: do not create orphan hours or audit the failed change.
        assertEquals(ErrorCode.LAB_NOT_FOUND, ex.getCode());
        verifyNoInteractions(hoursRepository, auditService);
    }

    @Test void setOperatingHours_existingRowIsUpdatedInsteadOfReplaced() {
        // Condition: Monday already has default hours.
        OperatingHours existing = new OperatingHours(); existing.setOpenTime(LocalTime.of(8, 0));
        existing.setCloseTime(LocalTime.of(17, 0));
        when(hoursRepository.findDefault(1)).thenReturn(Optional.of(existing));
        // Act: change closing time to 18:00.
        OperatingHours result = service.setOperatingHours(
                new OperatingHoursRequest(null, 1, LocalTime.of(8, 0), LocalTime.of(18, 0), false), 9L);
        // Confirmation: save the original row with the new hours.
        assertSame(existing, result);
        assertEquals(LocalTime.of(18, 0), existing.getCloseTime());
        verify(hoursRepository).save(existing);
    }

    @Test void addBlackout_blankReasonDoesNotSave() {
        // Condition: lab and future time range are valid, but the reason is blank.
        when(labRepository.existsById(10)).thenReturn(true);
        // Act: submit the blackout.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.addBlackout(
                new BlackoutRequest(10, Instant.parse("2026-10-20T02:00:00Z"),
                        Instant.parse("2026-10-20T04:00:00Z"), "  "), 9L));
        // Confirmation: reason validation precedes overlap lookup and write.
        assertEquals(ErrorCode.BLACKOUT_REASON_REQUIRED, ex.getCode());
        verifyNoInteractions(blackoutRepository, auditService);
    }

    @Test void checkLabOpen_crossingLocalMidnightIsRejected() {
        // Condition: range crosses midnight in the Vietnam business timezone.
        // Act: check a period that spans two local dates.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.checkLabOpen(10,
                Instant.parse("2026-10-19T16:30:00Z"), Instant.parse("2026-10-19T17:30:00Z")));
        // Confirmation: reject before accessing holiday, opening hours or blackout data.
        assertEquals(ErrorCode.TIME_OUTSIDE_OPENING_HOURS, ex.getCode());
        verifyNoInteractions(holidayRepository, hoursRepository, blackoutRepository);
    }

    @Test void setOperatingHours_labSpecificHalfHourWindowIsSaved() {
        // Condition: the lab exists and has no Tuesday-specific hours.
        when(labRepository.existsById(10)).thenReturn(true);
        when(hoursRepository.findForLab(10, 2)).thenReturn(Optional.empty());
        // Act: set an individual lab's 07:30-21:00 window.
        OperatingHours result = service.setOperatingHours(
                new OperatingHoursRequest(10, 2, LocalTime.of(7, 30), LocalTime.of(21, 0), false), 9L);
        // Confirmation: the lab-specific row is saved, not a default row.
        assertEquals(10, result.getLabId());
        assertEquals(LocalTime.of(7, 30), result.getOpenTime());
        verify(hoursRepository).save(result);
        verify(hoursRepository, never()).findDefault(anyInt());
    }

    @Test void addBlackout_touchingExistingEndIsAllowedWhenRepositoryReportsNoOverlap() {
        // Condition: the new range begins exactly at an existing blackout's end; half-open overlap is false.
        Instant start = Instant.parse("2026-10-20T02:00:00Z");
        Instant end = Instant.parse("2026-10-20T04:00:00Z");
        when(labRepository.existsById(10)).thenReturn(true);
        when(blackoutRepository.existsOverlap(10, start, end)).thenReturn(false);
        // Act: create the adjacent blackout.
        Blackout result = service.addBlackout(new BlackoutRequest(10, start, end, "Maintenance"), 9L);
        // Confirmation: exact endpoints reach the overlap query and the new range is saved.
        verify(blackoutRepository).existsOverlap(10, start, end);
        verify(blackoutRepository).save(result);
    }

    @Test void checkLabOpen_labSpecificHoursOverrideClosedDefault() {
        // Condition: lab 10 has Monday hours even though the default Monday is closed.
        OperatingHours specific = new OperatingHours();
        specific.setOpenTime(LocalTime.of(9, 0)); specific.setCloseTime(LocalTime.of(10, 0));
        when(hoursRepository.findForLab(10, 1)).thenReturn(Optional.of(specific));
        Instant start = Instant.parse("2026-10-19T02:00:00Z");
        Instant end = Instant.parse("2026-10-19T03:00:00Z");
        // Act: check exactly the lab-specific opening window in Vietnam time.
        assertDoesNotThrow(() -> service.checkLabOpen(10, start, end));
        // Confirmation: default hours are not consulted and the blackout check still runs.
        verify(hoursRepository, never()).findDefault(anyInt());
        verify(blackoutRepository).existsOverlap(10, start, end);
    }

    @Test void checkLabOpen_beforeOpeningIsRejected() {
        // Condition: default Monday hours start at 07:00 Vietnam time.
        OperatingHours hours = new OperatingHours(); hours.setOpenTime(LocalTime.of(7, 0)); hours.setCloseTime(LocalTime.of(21, 0));
        when(hoursRepository.findForLab(10, 1)).thenReturn(Optional.empty());
        when(hoursRepository.findDefault(1)).thenReturn(Optional.of(hours));
        // Act: start at 06:30 local time.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.checkLabOpen(10,
                Instant.parse("2026-10-18T23:30:00Z"), Instant.parse("2026-10-19T00:30:00Z")));
        // Confirmation: outside-hours code is returned before the blackout query.
        assertEquals(ErrorCode.TIME_OUTSIDE_OPENING_HOURS, ex.getCode());
        verifyNoInteractions(blackoutRepository);
    }

    @Test void checkLabOpen_afterClosingIsRejected() {
        // Condition: default Monday hours end at 21:00 Vietnam time.
        OperatingHours hours = new OperatingHours(); hours.setOpenTime(LocalTime.of(7, 0)); hours.setCloseTime(LocalTime.of(21, 0));
        when(hoursRepository.findForLab(10, 1)).thenReturn(Optional.empty());
        when(hoursRepository.findDefault(1)).thenReturn(Optional.of(hours));
        // Act: end at 21:30 local time.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.checkLabOpen(10,
                Instant.parse("2026-10-19T13:30:00Z"), Instant.parse("2026-10-19T14:30:00Z")));
        // Confirmation: outside-hours code is returned without querying blackouts.
        assertEquals(ErrorCode.TIME_OUTSIDE_OPENING_HOURS, ex.getCode());
        verifyNoInteractions(blackoutRepository);
    }

    @Test void checkLabOpen_closedWeekdayIsRejected() {
        // Condition: Monday has an explicitly closed lab-specific row.
        OperatingHours closed = new OperatingHours(); closed.setClosed(true);
        when(hoursRepository.findForLab(10, 1)).thenReturn(Optional.of(closed));
        // Act: check a period on that weekday.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.checkLabOpen(10,
                Instant.parse("2026-10-19T02:00:00Z"), Instant.parse("2026-10-19T03:00:00Z")));
        // Confirmation: closed day is rejected and default hours cannot override the lab row.
        assertEquals(ErrorCode.TIME_OUTSIDE_OPENING_HOURS, ex.getCode());
        verify(hoursRepository, never()).findDefault(anyInt());
        verifyNoInteractions(blackoutRepository);
    }

    @Test void checkLabOpen_invalidRangeIsRejectedBeforeLookups() {
        // Condition: start and end are equal.
        Instant at = Instant.parse("2026-10-19T02:00:00Z");
        // Act: check the empty interval.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.checkLabOpen(10, at, at));
        // Confirmation: invalid range code is returned without consulting the calendar.
        assertEquals(ErrorCode.TIME_RANGE_INVALID, ex.getCode());
        verifyNoInteractions(holidayRepository, hoursRepository, blackoutRepository);
    }
}
