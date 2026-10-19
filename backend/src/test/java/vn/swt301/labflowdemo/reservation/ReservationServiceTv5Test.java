package vn.swt301.labflowdemo.reservation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.swt301.labflowdemo.audit.AuditService;
import vn.swt301.labflowdemo.catalog.*;
import vn.swt301.labflowdemo.catalog.CalendarService;
import vn.swt301.labflowdemo.catalog.LabService;
import vn.swt301.labflowdemo.catalog.BookingPolicy;
import vn.swt301.labflowdemo.common.BusinessException;
import vn.swt301.labflowdemo.common.ErrorCode;
import vn.swt301.labflowdemo.reservation.ReservationDtos.CreateReservationRequest;
import vn.swt301.labflowdemo.reservation.ReservationDtos.ReservationView;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTv5Test {
    @Mock ReservationRepository reservationRepository;
    @Mock ReservationItemRepository itemRepository;
    @Mock LabService labService;
    @Mock CalendarService calendarService;
    @Mock BookingPolicy bookingPolicy;
    @Mock AuditService auditService;
    private ReservationService service;
    private final Instant start = Instant.parse("2026-10-20T02:00:00Z");
    private final Instant end = Instant.parse("2026-10-20T03:00:00Z");

    @BeforeEach
    void setUp() { service = new ReservationService(reservationRepository, itemRepository, labService, calendarService, bookingPolicy, auditService,
            Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC)); }

    @Test
    void utcid71_N_createReservation_confirmsNonApprovalLabAndSavesItem() {
        // Arrange: valid key, active lab without approval requirement, open calendar, and free slot.
        Lab lab = new Lab(); lab.setId(10); lab.setStatus(ActiveStatus.ACTIVE); lab.setRequiresApproval(false);
        when(reservationRepository.findByIdempotencyKey("idem-001")).thenReturn(Optional.empty());
        when(labService.getLab(10)).thenReturn(lab);
        when(itemRepository.existsLabOverlap(10, start, end)).thenReturn(false);
        doAnswer(inv -> { ((Reservation) inv.getArgument(0)).setId(51L); return inv.getArgument(0); }).when(reservationRepository).saveAndFlush(any());
        // Condition: create a reservation with a valid purpose and idempotency key.
        ReservationView result = service.createReservation(77L, new CreateReservationRequest(10, start, end, "Lab practice"), "idem-001");
        // Confirmation: reservation is CONFIRMED and its item is flushed for the lab.
        assertAll(() -> assertEquals(51L, result.id()), () -> assertEquals("CONFIRMED", result.status()),
                () -> assertEquals(10, result.labId()), () -> assertFalse(result.replayed()));
        verify(bookingPolicy).checkPeriod(start, end);
        verify(calendarService).checkLabOpen(10, start, end);
        verify(itemRepository).saveAndFlush(argThat(item -> item.getLabId().equals(10) && item.isActive()));
    }

    @Test
    void utcid72_A_createReservation_returnsPendingWhenLabNeedsApproval() {
        // Arrange: active lab requires approval and the requested slot is free.
        Lab lab = new Lab(); lab.setId(10); lab.setStatus(ActiveStatus.ACTIVE); lab.setRequiresApproval(true);
        when(reservationRepository.findByIdempotencyKey("idem-002")).thenReturn(Optional.empty());
        when(labService.getLab(10)).thenReturn(lab);
        when(itemRepository.existsLabOverlap(10, start, end)).thenReturn(false);
        doAnswer(inv -> { ((Reservation) inv.getArgument(0)).setId(52L); return inv.getArgument(0); }).when(reservationRepository).saveAndFlush(any());
        // Condition: create a reservation for the approval-required lab.
        ReservationView result = service.createReservation(77L, new CreateReservationRequest(10, start, end, "Exam setup"), "idem-002");
        // Confirmation: status is PENDING, while the same persistence path is used.
        assertEquals("PENDING", result.status());
        verify(reservationRepository).saveAndFlush(any(Reservation.class));
        verify(itemRepository).saveAndFlush(any(ReservationItem.class));
    }

    @Test
    void utcid73_B_createReservation_rejectsInvalidIdempotencyKeyWithErrorCode() {
        // Arrange: key is shorter than the service's minimum of eight characters.
        // Condition: attempt reservation creation with the invalid key.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createReservation(77L,
                new CreateReservationRequest(10, start, end, "Lab practice"), "short"));
        // Confirmation: RES_IDEMPOTENCY_KEY_REQUIRED is raised before any dependency call.
        assertEquals(ErrorCode.RES_IDEMPOTENCY_KEY_REQUIRED, ex.getCode());
        verifyNoInteractions(reservationRepository, labService, bookingPolicy, calendarService);
    }
}
