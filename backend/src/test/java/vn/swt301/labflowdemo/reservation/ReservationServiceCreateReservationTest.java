package vn.swt301.labflowdemo.reservation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import vn.swt301.labflowdemo.audit.AuditService;
import vn.swt301.labflowdemo.catalog.ActiveStatus;
import vn.swt301.labflowdemo.catalog.BookingPolicy;
import vn.swt301.labflowdemo.catalog.CalendarService;
import vn.swt301.labflowdemo.catalog.Lab;
import vn.swt301.labflowdemo.catalog.LabService;
import vn.swt301.labflowdemo.common.BusinessException;
import vn.swt301.labflowdemo.common.ErrorCode;
import vn.swt301.labflowdemo.reservation.ReservationDtos.CreateReservationRequest;
import vn.swt301.labflowdemo.reservation.ReservationDtos.ReservationView;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test cho {@link ReservationService#createReservation(Long, CreateReservationRequest, String)}
 * (sheet "createReservation"). BookingPolicy / CalendarService / các repository đều là mock:
 * test này chỉ kiểm tra thứ tự các bước, ánh xạ lỗi và dữ liệu được ghi.
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceCreateReservationTest {

    static final Instant NOW = Instant.parse("2026-10-12T02:00:00Z");     // 09:00 thứ Hai giờ VN
    static final Instant START = Instant.parse("2026-10-13T02:00:00Z");   // 09:00 thứ Ba
    static final Instant END = Instant.parse("2026-10-13T04:00:00Z");     // 11:00 thứ Ba
    static final Long USER = 5L;
    static final String KEY = "key-12345";   // 9 ký tự

    @Mock ReservationRepository reservationRepository;
    @Mock ReservationItemRepository itemRepository;
    @Mock LabService labService;
    @Mock CalendarService calendarService;
    @Mock BookingPolicy bookingPolicy;
    @Mock AuditService auditService;

    ReservationService service;

    @BeforeEach
    void setUp() {
        service = new ReservationService(reservationRepository, itemRepository, labService, calendarService,
                bookingPolicy, auditService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CreateReservationRequest req(String purpose) {
        return new CreateReservationRequest(7, START, END, purpose);
    }

    private static Lab lab(ActiveStatus status, boolean requiresApproval) {
        Lab l = new Lab();
        l.setId(7);
        l.setStatus(status);
        l.setRequiresApproval(requiresApproval);
        return l;
    }

    /** Condition "đường đi đẹp": lab 7 ACTIVE, key mới, không trùng giờ, DB ghi được (gán id 100). */
    private void happyPath(boolean requiresApproval) {
        when(labService.getLab(7)).thenReturn(lab(ActiveStatus.ACTIVE, requiresApproval));
        when(itemRepository.existsLabOverlap(7, START, END)).thenReturn(false);
        when(reservationRepository.saveAndFlush(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            r.setId(100L);
            return r;
        });
    }

    private void assertFails(String key, CreateReservationRequest request, ErrorCode expected) {
        assertThatThrownBy(() -> service.createReservation(USER, request, key))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(expected);
        verify(reservationRepository, never()).saveAndFlush(any());
        verify(itemRepository, never()).saveAndFlush(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("UTCID01 [N] valid request, lab does not need approval -> CONFIRMED, replayed=false, audit written")
    void utcid01_valid_isConfirmed() {
        happyPath(false);

        ReservationView res = service.createReservation(USER, req("Thực hành SWT301"), KEY);

        assertThat(res.id()).isEqualTo(100L);
        assertThat(res.userId()).isEqualTo(USER);
        assertThat(res.labId()).isEqualTo(7);
        assertThat(res.status()).isEqualTo("CONFIRMED");
        assertThat(res.startAt()).isEqualTo(START);
        assertThat(res.endAt()).isEqualTo(END);
        assertThat(res.purpose()).isEqualTo("Thực hành SWT301");
        assertThat(res.replayed()).isFalse();
        verify(itemRepository).saveAndFlush(any(ReservationItem.class));
        verify(auditService).record(USER, "RESERVATION", 100L, "CREATE", null, res);
        InOrder order = inOrder(bookingPolicy, calendarService, itemRepository);
        order.verify(bookingPolicy).checkPeriod(START, END);
        order.verify(calendarService).checkLabOpen(7, START, END);
        order.verify(itemRepository).existsLabOverlap(7, START, END);
    }

    @Test
    @DisplayName("UTCID02 [N] lab requires approval (BR-06) -> status PENDING")
    void utcid02_labRequiresApproval_isPending() {
        happyPath(true);

        ReservationView res = service.createReservation(USER, req("Thực hành"), KEY);

        assertThat(res.status()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("UTCID03 [N] key and purpose have surrounding spaces -> both trimmed before use/storage")
    void utcid03_keyAndPurposeTrimmed() {
        happyPath(false);

        ReservationView res = service.createReservation(USER, req("   Họp nhóm   "), "  key-12345  ");

        assertThat(res.purpose()).isEqualTo("Họp nhóm");
        verify(reservationRepository).findByIdempotencyKey("key-12345");
    }

    @Test
    @DisplayName("UTCID04 [B] Idempotency-Key of exactly 8 characters -> accepted")
    void utcid04_key8_isAccepted() {
        happyPath(false);

        ReservationView res = service.createReservation(USER, req("Thực hành"), "12345678");

        assertThat(res.id()).isEqualTo(100L);
    }

    @Test
    @DisplayName("UTCID05 [B] Idempotency-Key of exactly 64 characters -> accepted")
    void utcid05_key64_isAccepted() {
        happyPath(false);

        ReservationView res = service.createReservation(USER, req("Thực hành"), "k".repeat(64));

        assertThat(res.id()).isEqualTo(100L);
    }

    @Test
    @DisplayName("UTCID06 [B] Idempotency-Key of 7 characters -> RES_IDEMPOTENCY_KEY_REQUIRED")
    void utcid06_key7_throwsKeyRequired() {
        assertFails("1234567", req("Thực hành"), ErrorCode.RES_IDEMPOTENCY_KEY_REQUIRED);
        verify(reservationRepository, never()).findByIdempotencyKey(any());
    }

    @Test
    @DisplayName("UTCID07 [B] Idempotency-Key of 65 characters -> RES_IDEMPOTENCY_KEY_REQUIRED")
    void utcid07_key65_throwsKeyRequired() {
        assertFails("k".repeat(65), req("Thực hành"), ErrorCode.RES_IDEMPOTENCY_KEY_REQUIRED);
    }

    @Test
    @DisplayName("UTCID08 [A] Idempotency-Key = null -> RES_IDEMPOTENCY_KEY_REQUIRED")
    void utcid08_keyNull_throwsKeyRequired() {
        assertFails(null, req("Thực hành"), ErrorCode.RES_IDEMPOTENCY_KEY_REQUIRED);
    }

    @Test
    @DisplayName("UTCID09 [A] Idempotency-Key is only spaces -> RES_IDEMPOTENCY_KEY_REQUIRED")
    void utcid09_keyBlank_throwsKeyRequired() {
        assertFails("          ", req("Thực hành"), ErrorCode.RES_IDEMPOTENCY_KEY_REQUIRED);
    }

    @Test
    @DisplayName("UTCID10 [N] same key + same user -> first result returned (replayed=true), nothing is saved again")
    void utcid10_sameKeySameUser_isReplayed() {
        Reservation old = new Reservation();
        old.setId(100L);
        old.setUserId(USER);
        old.setStatus(ReservationStatus.CONFIRMED);
        old.setStartAt(START);
        old.setEndAt(END);
        old.setPurpose("Thực hành");
        ReservationItem item = new ReservationItem();
        item.setLabId(7);
        when(reservationRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(old));
        when(itemRepository.findByReservationId(100L)).thenReturn(List.of(item));

        ReservationView res = service.createReservation(USER, req("Mục đích khác"), KEY);

        assertThat(res.replayed()).isTrue();
        assertThat(res.id()).isEqualTo(100L);
        assertThat(res.labId()).isEqualTo(7);
        assertThat(res.purpose()).isEqualTo("Thực hành");     // lấy kết quả cũ, không phải request mới
        verify(reservationRepository, never()).saveAndFlush(any());
        verify(labService, never()).getLab(any());
    }

    @Test
    @DisplayName("UTCID11 [A] key already used by ANOTHER user -> RES_IDEMPOTENCY_KEY_REQUIRED 'Idempotency-Key already used'")
    void utcid11_keyOfAnotherUser_throwsKeyRequired() {
        Reservation old = new Reservation();
        old.setId(100L);
        old.setUserId(99L);
        when(reservationRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(old));

        assertThatThrownBy(() -> service.createReservation(USER, req("Thực hành"), KEY))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Idempotency-Key already used")
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.RES_IDEMPOTENCY_KEY_REQUIRED);
        verify(reservationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("UTCID12 [A] purpose = null -> RES_PURPOSE_REQUIRED")
    void utcid12_purposeNull_throwsPurposeRequired() {
        assertFails(KEY, req(null), ErrorCode.RES_PURPOSE_REQUIRED);
        verify(labService, never()).getLab(any());
    }

    @Test
    @DisplayName("UTCID13 [A] purpose is only spaces -> RES_PURPOSE_REQUIRED")
    void utcid13_purposeBlank_throwsPurposeRequired() {
        assertFails(KEY, req("     "), ErrorCode.RES_PURPOSE_REQUIRED);
    }

    @Test
    @DisplayName("UTCID14 [B] purpose of exactly 255 characters -> accepted")
    void utcid14_purpose255_isAccepted() {
        happyPath(false);

        ReservationView res = service.createReservation(USER, req("p".repeat(255)), KEY);

        assertThat(res.purpose()).hasSize(255);
    }

    @Test
    @DisplayName("UTCID15 [B] purpose of 256 characters -> RES_PURPOSE_REQUIRED")
    void utcid15_purpose256_throwsPurposeRequired() {
        assertFails(KEY, req("p".repeat(256)), ErrorCode.RES_PURPOSE_REQUIRED);
    }

    @Test
    @DisplayName("UTCID16 [A] lab does not exist -> LAB_NOT_FOUND (from LabService), nothing saved")
    void utcid16_labNotFound_throwsLabNotFound() {
        when(labService.getLab(7)).thenThrow(new BusinessException(ErrorCode.LAB_NOT_FOUND));

        assertFails(KEY, req("Thực hành"), ErrorCode.LAB_NOT_FOUND);
        verify(bookingPolicy, never()).checkPeriod(any(), any());
    }

    @Test
    @DisplayName("UTCID17 [A] lab exists but status is CLOSED (not ACTIVE) -> LAB_INACTIVE")
    void utcid17_labInactive_throwsLabInactive() {
        when(labService.getLab(7)).thenReturn(lab(ActiveStatus.CLOSED, false));

        assertFails(KEY, req("Thực hành"), ErrorCode.LAB_INACTIVE);
        verify(bookingPolicy, never()).checkPeriod(any(), any());
    }

    @Test
    @DisplayName("UTCID18 [A] BookingPolicy rejects the period (TIME_IN_PAST) -> error passes through, calendar not asked")
    void utcid18_policyRejects_errorPassesThrough() {
        when(labService.getLab(7)).thenReturn(lab(ActiveStatus.ACTIVE, false));
        doThrow(new BusinessException(ErrorCode.TIME_IN_PAST)).when(bookingPolicy).checkPeriod(START, END);

        assertFails(KEY, req("Thực hành"), ErrorCode.TIME_IN_PAST);
        verify(calendarService, never()).checkLabOpen(any(), any(), any());
    }

    @Test
    @DisplayName("UTCID19 [A] lab closed in that period (TIME_OUTSIDE_OPENING_HOURS) -> error passes through, overlap not checked")
    void utcid19_labClosed_errorPassesThrough() {
        when(labService.getLab(7)).thenReturn(lab(ActiveStatus.ACTIVE, false));
        doThrow(new BusinessException(ErrorCode.TIME_OUTSIDE_OPENING_HOURS)).when(calendarService).checkLabOpen(7, START, END);

        assertFails(KEY, req("Thực hành"), ErrorCode.TIME_OUTSIDE_OPENING_HOURS);
        verify(itemRepository, never()).existsLabOverlap(any(), any(), any());
    }

    @Test
    @DisplayName("UTCID20 [A] slot already booked (existsLabOverlap = true) -> RES_CONFLICT, nothing saved")
    void utcid20_overlap_throwsConflict() {
        when(labService.getLab(7)).thenReturn(lab(ActiveStatus.ACTIVE, false));
        when(itemRepository.existsLabOverlap(7, START, END)).thenReturn(true);

        assertFails(KEY, req("Thực hành"), ErrorCode.RES_CONFLICT);
    }

    @Test
    @DisplayName("UTCID21 [A] lost the race: DB rejects the item (exclusion constraint) -> RES_CONFLICT, no audit")
    void utcid21_exclusionViolation_throwsConflict() {
        happyPath(false);
        when(itemRepository.saveAndFlush(any(ReservationItem.class))).thenThrow(new DataIntegrityViolationException("23P01"));

        assertThatThrownBy(() -> service.createReservation(USER, req("Thực hành"), KEY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.RES_CONFLICT);
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("UTCID22 [A] lost the race: deadlock / lock failure while inserting -> RES_CONFLICT")
    void utcid22_lockFailure_throwsConflict() {
        happyPath(false);
        when(itemRepository.saveAndFlush(any(ReservationItem.class))).thenThrow(new CannotAcquireLockException("40P01"));

        assertThatThrownBy(() -> service.createReservation(USER, req("Thực hành"), KEY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.RES_CONFLICT);
    }

    @Test
    @DisplayName("UTCID23 [A] DB rejects the reservation row itself (data integrity) -> RES_CONFLICT, item never inserted")
    void utcid23_reservationRowRejected_throwsConflict() {
        when(labService.getLab(7)).thenReturn(lab(ActiveStatus.ACTIVE, false));
        when(reservationRepository.saveAndFlush(any(Reservation.class))).thenThrow(new DataIntegrityViolationException("dup key"));

        assertThatThrownBy(() -> service.createReservation(USER, req("Thực hành"), KEY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.RES_CONFLICT);
        verify(itemRepository, never()).saveAndFlush(any());
    }
}
