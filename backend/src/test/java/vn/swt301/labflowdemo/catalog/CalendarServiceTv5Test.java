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
}
