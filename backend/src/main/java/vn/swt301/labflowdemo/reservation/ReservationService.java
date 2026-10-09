package vn.swt301.labflowdemo.reservation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.swt301.labflowdemo.audit.AuditService;
import vn.swt301.labflowdemo.catalog.ActiveStatus;
import vn.swt301.labflowdemo.catalog.BookingPolicy;
import vn.swt301.labflowdemo.catalog.CalendarService;
import vn.swt301.labflowdemo.catalog.Lab;
import vn.swt301.labflowdemo.catalog.LabService;
import vn.swt301.labflowdemo.common.BusinessException;
import vn.swt301.labflowdemo.common.ErrorCode;
import vn.swt301.labflowdemo.common.Texts;
import vn.swt301.labflowdemo.reservation.ReservationDtos.CreateReservationRequest;
import vn.swt301.labflowdemo.reservation.ReservationDtos.ReservationView;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Reservation - TRANSACTION SPIKE version (E1 day 11, G0). Only "create a lab reservation" exists yet;
 * cancel / approve / check-in / waitlist come with the reservation engine (E4, week 5 of the plan).
 * <p>
 * Anti double-booking has two layers:
 * <ol>
 *   <li>a quick check in Java ({@code existsLabOverlap}) for a friendly error in the normal case;</li>
 *   <li>the PostgreSQL exclusion constraint {@code ex_lab_no_overlap} - the real guarantee when two
 *       requests arrive at the same time (cả hai cùng qua bước 1, chỉ một dòng được ghi).</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationItemRepository itemRepository;
    private final LabService labService;
    private final CalendarService calendarService;
    private final BookingPolicy bookingPolicy;
    private final AuditService auditService;
    private final Clock clock;

    /**
     * Books a lab for [startAt, endAt). Labs that need approval (BR-06) start PENDING, others CONFIRMED.
     * Sending the same Idempotency-Key again returns the first result instead of a second booking.
     *
     * @throws BusinessException RES_IDEMPOTENCY_KEY_REQUIRED, RES_PURPOSE_REQUIRED, LAB_NOT_FOUND, LAB_INACTIVE,
     *                           TIME_* (BookingPolicy, CalendarService), RES_CONFLICT
     */
    @Transactional
    public ReservationView createReservation(Long userId, CreateReservationRequest request, String idempotencyKey) {
        String key = Texts.clean(idempotencyKey);
        if (key == null || key.length() < 8 || key.length() > 64) {
            throw new BusinessException(ErrorCode.RES_IDEMPOTENCY_KEY_REQUIRED);
        }
        Optional<Reservation> previous = reservationRepository.findByIdempotencyKey(key);
        if (previous.isPresent()) {
            if (!previous.get().getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.RES_IDEMPOTENCY_KEY_REQUIRED, "Idempotency-Key already used");
            }
            log.info("Reservation replayed: id={}, key={}", previous.get().getId(), key);
            return view(previous.get(), labOf(previous.get()), true);
        }
        String purpose = Texts.clean(request.purpose());
        if (purpose == null || purpose.length() > 255) {
            throw new BusinessException(ErrorCode.RES_PURPOSE_REQUIRED);
        }
        Lab lab = labService.getLab(request.labId());
        if (lab.getStatus() != ActiveStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.LAB_INACTIVE);
        }
        bookingPolicy.checkPeriod(request.startAt(), request.endAt());
        calendarService.checkLabOpen(lab.getId(), request.startAt(), request.endAt());
        if (itemRepository.existsLabOverlap(lab.getId(), request.startAt(), request.endAt())) {
            throw new BusinessException(ErrorCode.RES_CONFLICT);
        }

        Reservation reservation = new Reservation();
        reservation.setUserId(userId);
        reservation.setStatus(lab.isRequiresApproval() ? ReservationStatus.PENDING : ReservationStatus.CONFIRMED);
        reservation.setStartAt(request.startAt());
        reservation.setEndAt(request.endAt());
        reservation.setPurpose(purpose);
        reservation.setIdempotencyKey(key);
        reservation.setCreatedAt(Instant.now(clock));
        ReservationItem item = new ReservationItem();
        item.setLabId(lab.getId());
        item.setStartAt(request.startAt());
        item.setEndAt(request.endAt());
        item.setActive(true);
        try {
            reservationRepository.saveAndFlush(reservation);
            item.setReservationId(reservation.getId());
            itemRepository.saveAndFlush(item);   // flush NOW so the exclusion constraint fires inside this method
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException e) {
            // 23P01 exclusion_violation, hoặc 40P01 deadlock khi nhiều request chèn cùng lúc: PostgreSQL đã
            // huỷ transaction này - request này THUA cuộc đua, đúng một request khác đã giữ slot.
            log.warn("Reservation lost the race: labId={}, start={}, key={}", lab.getId(), request.startAt(), key);
            throw new BusinessException(ErrorCode.RES_CONFLICT);
        }
        auditService.record(userId, "RESERVATION", reservation.getId(), "CREATE", null,
                view(reservation, lab.getId(), false));
        log.info("Reservation created: id={}, labId={}, status={}, userId={}", reservation.getId(), lab.getId(),
                reservation.getStatus(), userId);
        return view(reservation, lab.getId(), false);
    }

    @Transactional(readOnly = true)
    public List<ReservationView> myReservations(Long userId) {
        return reservationRepository.findByUserIdOrderByStartAtDesc(userId).stream()
                .map(r -> view(r, labOf(r), false))
                .toList();
    }

    private Integer labOf(Reservation r) {
        return itemRepository.findByReservationId(r.getId()).stream()
                .map(ReservationItem::getLabId).findFirst().orElse(null);
    }

    private static ReservationView view(Reservation r, Integer labId, boolean replayed) {
        return new ReservationView(r.getId(), r.getUserId(), labId, r.getStatus().name(), r.getStartAt(),
                r.getEndAt(), r.getPurpose(), replayed);
    }
}
