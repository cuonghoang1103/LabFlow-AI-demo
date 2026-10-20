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

    @Test
    void replaySameUser_returnsExistingReservationWithoutNewWrite() {
        // Given: this user already created a reservation with the same key.
        Reservation previous = new Reservation(); previous.setId(51L); previous.setUserId(77L);
        previous.setStatus(ReservationStatus.CONFIRMED); previous.setStartAt(start); previous.setEndAt(end);
        previous.setPurpose("Original purpose");
        when(reservationRepository.findByIdempotencyKey("idem-001")).thenReturn(Optional.of(previous));
        ReservationItem item = new ReservationItem(); item.setLabId(10);
        when(itemRepository.findByReservationId(51L)).thenReturn(java.util.List.of(item));
        // When: the same key is submitted again, even with a different request body.
        ReservationView result = service.createReservation(77L,
                new CreateReservationRequest(10, start, end, "Different purpose"), "idem-001");
        // Then: return the ORIGINAL result; do not create a second booking or audit entry.
        assertTrue(result.replayed()); assertEquals(51L, result.id());
        assertEquals("Original purpose", result.purpose()); assertEquals(10, result.labId());
        verify(reservationRepository, never()).saveAndFlush(any());
        verifyNoInteractions(labService, bookingPolicy, calendarService, auditService);
    }

    @Test
    void keyOwnedByOtherUser_isRejectedBeforeCreatingAnything() {
        // Given: user 77 owns this idempotency key; user 88 must not reuse it.
        Reservation previous = new Reservation(); previous.setUserId(77L);
        when(reservationRepository.findByIdempotencyKey("idem-001")).thenReturn(Optional.of(previous));
        // When/then: reject the collision without querying lab availability or saving a new booking.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createReservation(88L,
                new CreateReservationRequest(10, start, end, "Lab practice"), "idem-001"));
        assertEquals(ErrorCode.RES_IDEMPOTENCY_KEY_REQUIRED, ex.getCode());
        verifyNoInteractions(itemRepository, labService, bookingPolicy, calendarService, auditService);
    }

    @Test
    void emptyPurpose_isRejectedBeforeLookingUpLab() {
        // Given: valid key but whitespace-only purpose.
        when(reservationRepository.findByIdempotencyKey("idem-001")).thenReturn(Optional.empty());
        // When/then: no lab check or reservation write should happen.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createReservation(77L,
                new CreateReservationRequest(10, start, end, "  "), "idem-001"));
        assertEquals(ErrorCode.RES_PURPOSE_REQUIRED, ex.getCode());
        verifyNoInteractions(labService, itemRepository, auditService);
    }

    @Test
    void overlap_isRejectedBeforePersistence() {
        // Given: an active lab already has an item overlapping the requested time.
        Lab lab = new Lab(); lab.setId(10); lab.setStatus(ActiveStatus.ACTIVE);
        when(reservationRepository.findByIdempotencyKey("idem-001")).thenReturn(Optional.empty());
        when(labService.getLab(10)).thenReturn(lab);
        when(itemRepository.existsLabOverlap(10, start, end)).thenReturn(true);
        // When/then: a conflicting booking must not be saved.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createReservation(77L,
                new CreateReservationRequest(10, start, end, "Lab practice"), "idem-001"));
        assertEquals(ErrorCode.RES_CONFLICT, ex.getCode());
        verify(reservationRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void databaseExclusionConflict_isMappedToBusinessError() {
        // Given: pre-check sees no overlap, but the database rejects the item during flush (racing request).
        Lab lab = new Lab(); lab.setId(10); lab.setStatus(ActiveStatus.ACTIVE);
        when(reservationRepository.findByIdempotencyKey("idem-001")).thenReturn(Optional.empty());
        when(labService.getLab(10)).thenReturn(lab);
        when(itemRepository.existsLabOverlap(10, start, end)).thenReturn(false);
        doAnswer(inv -> { ((Reservation) inv.getArgument(0)).setId(51L); return inv.getArgument(0); })
                .when(reservationRepository).saveAndFlush(any());
        when(itemRepository.saveAndFlush(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("23P01"));
        // When/then: translate DB exclusion failure to the public conflict code, without success audit.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createReservation(77L,
                new CreateReservationRequest(10, start, end, "Lab practice"), "idem-001"));
        assertEquals(ErrorCode.RES_CONFLICT, ex.getCode());
        verifyNoInteractions(auditService);
    }

    @Test void keyAtMaximumLengthIsAccepted() {
        // Condition: 64 characters is the inclusive maximum allowed idempotency-key length.
        String key = "k".repeat(64);
        Lab lab = new Lab(); lab.setId(10); lab.setStatus(ActiveStatus.ACTIVE);
        when(reservationRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(labService.getLab(10)).thenReturn(lab);
        // Act: create a booking with the boundary key.
        ReservationView result = service.createReservation(77L,
                new CreateReservationRequest(10, start, end, "Practice"), key);
        // Confirmation: the key is retained and the booking is not a replay.
        assertFalse(result.replayed());
        verify(reservationRepository).saveAndFlush(argThat(r -> key.equals(r.getIdempotencyKey())));
    }

    @Test void keyAboveMaximumLengthIsRejectedBeforeLookup() {
        // Condition: 65 characters exceed the maximum key length by one.
        // Act: attempt to reserve with the invalid key.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createReservation(77L,
                new CreateReservationRequest(10, start, end, "Practice"), "k".repeat(65)));
        // Confirmation: fail before any dependency interaction.
        assertEquals(ErrorCode.RES_IDEMPOTENCY_KEY_REQUIRED, ex.getCode());
        verifyNoInteractions(reservationRepository, labService, bookingPolicy, calendarService);
    }

    @Test void inactiveLabDoesNotReachTimeOrAvailabilityChecks() {
        // Condition: the requested lab exists but is closed.
        Lab lab = new Lab(); lab.setId(10); lab.setStatus(ActiveStatus.CLOSED);
        when(reservationRepository.findByIdempotencyKey("idem-001")).thenReturn(Optional.empty());
        when(labService.getLab(10)).thenReturn(lab);
        // Act: attempt to book the inactive lab.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createReservation(77L,
                new CreateReservationRequest(10, start, end, "Practice"), "idem-001"));
        // Confirmation: no period check, persistence or success audit.
        assertEquals(ErrorCode.LAB_INACTIVE, ex.getCode());
        verifyNoInteractions(bookingPolicy, calendarService, itemRepository, auditService);
    }

    @Test void purposeAboveMaximumLengthIsRejected() {
        // Condition: purpose is 256 characters, one above the 255-character limit.
        when(reservationRepository.findByIdempotencyKey("idem-001")).thenReturn(Optional.empty());
        // Act: attempt to reserve using an overlong purpose.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createReservation(77L,
                new CreateReservationRequest(10, start, end, "x".repeat(256)), "idem-001"));
        // Confirmation: no lab lookup or booking write.
        assertEquals(ErrorCode.RES_PURPOSE_REQUIRED, ex.getCode());
        verifyNoInteractions(labService, itemRepository, auditService);
    }

    @Test void keyAtMinimumLengthIsAccepted() {
        // Condition: eight characters are the inclusive minimum for a new key.
        String key = "12345678";
        Lab lab = new Lab(); lab.setId(10); lab.setStatus(ActiveStatus.ACTIVE);
        when(reservationRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(labService.getLab(10)).thenReturn(lab);
        // Act: create a reservation using that boundary key.
        ReservationView result = service.createReservation(77L,
                new CreateReservationRequest(10, start, end, "Practice"), key);
        // Confirmation: booking succeeds with the unmodified key rather than being rejected as too short.
        assertFalse(result.replayed());
        verify(reservationRepository).saveAndFlush(argThat(r -> key.equals(r.getIdempotencyKey())));
    }

    @Test void policyFailureStopsBeforeCalendarAndPersistence() {
        // Condition: an active lab is available, but the policy rejects an off-grid period.
        Lab lab = new Lab(); lab.setId(10); lab.setStatus(ActiveStatus.ACTIVE);
        when(reservationRepository.findByIdempotencyKey("idem-001")).thenReturn(Optional.empty());
        when(labService.getLab(10)).thenReturn(lab);
        doThrow(new BusinessException(ErrorCode.TIME_SLOT_MISALIGNED)).when(bookingPolicy).checkPeriod(start, end);
        // Act: request the rejected period.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createReservation(77L,
                new CreateReservationRequest(10, start, end, "Practice"), "idem-001"));
        // Confirmation: the policy error propagates before calendar lookup, item check or writes.
        assertEquals(ErrorCode.TIME_SLOT_MISALIGNED, ex.getCode());
        verifyNoInteractions(calendarService, itemRepository, auditService);
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test void calendarFailureStopsBeforeOverlapAndPersistence() {
        // Condition: the period passes policy but the lab calendar reports a blackout.
        Lab lab = new Lab(); lab.setId(10); lab.setStatus(ActiveStatus.ACTIVE);
        when(reservationRepository.findByIdempotencyKey("idem-001")).thenReturn(Optional.empty());
        when(labService.getLab(10)).thenReturn(lab);
        doThrow(new BusinessException(ErrorCode.TIME_BLACKOUT)).when(calendarService).checkLabOpen(10, start, end);
        // Act: request the blocked time.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createReservation(77L,
                new CreateReservationRequest(10, start, end, "Practice"), "idem-001"));
        // Confirmation: the calendar code propagates without querying overlap or saving anything.
        assertEquals(ErrorCode.TIME_BLACKOUT, ex.getCode());
        verify(bookingPolicy).checkPeriod(start, end);
        verifyNoInteractions(itemRepository, auditService);
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test void adjacentSlotIsAcceptedWhenRepositoryReportsNoOverlap() {
        // Condition: an existing booking ends exactly at start; the half-open repository query finds no overlap.
        Lab lab = new Lab(); lab.setId(10); lab.setStatus(ActiveStatus.ACTIVE);
        when(reservationRepository.findByIdempotencyKey("idem-001")).thenReturn(Optional.empty());
        when(labService.getLab(10)).thenReturn(lab);
        when(itemRepository.existsLabOverlap(10, start, end)).thenReturn(false);
        // Act: reserve the adjacent time window.
        ReservationView result = service.createReservation(77L,
                new CreateReservationRequest(10, start, end, "Practice"), "idem-001");
        // Confirmation: exact endpoints reach the overlap query and a new item is persisted.
        assertFalse(result.replayed());
        verify(itemRepository).existsLabOverlap(10, start, end);
        verify(itemRepository).saveAndFlush(any(ReservationItem.class));
    }

    @Test void deadlockDuringItemFlushIsMappedToConflict() {
        // Condition: the Java overlap check passes but a concurrent DB write deadlocks on item flush.
        Lab lab = new Lab(); lab.setId(10); lab.setStatus(ActiveStatus.ACTIVE);
        when(reservationRepository.findByIdempotencyKey("idem-001")).thenReturn(Optional.empty());
        when(labService.getLab(10)).thenReturn(lab);
        when(itemRepository.saveAndFlush(any())).thenThrow(
                new org.springframework.dao.PessimisticLockingFailureException("40P01"));
        // Act: create the reservation while the database reports the race.
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createReservation(77L,
                new CreateReservationRequest(10, start, end, "Practice"), "idem-001"));
        // Confirmation: the race maps to RES_CONFLICT and no success audit is recorded.
        assertEquals(ErrorCode.RES_CONFLICT, ex.getCode());
        verifyNoInteractions(auditService);
    }
}
