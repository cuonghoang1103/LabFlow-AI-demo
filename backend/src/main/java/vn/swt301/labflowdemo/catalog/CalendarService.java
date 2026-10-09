package vn.swt301.labflowdemo.catalog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.swt301.labflowdemo.audit.AuditService;
import vn.swt301.labflowdemo.catalog.CalendarDtos.BlackoutRequest;
import vn.swt301.labflowdemo.catalog.CalendarDtos.OperatingHoursRequest;
import vn.swt301.labflowdemo.common.BusinessException;
import vn.swt301.labflowdemo.common.ErrorCode;
import vn.swt301.labflowdemo.common.Texts;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Operating calendar (E3 day 23, screen S18 - owner C2): opening hours per weekday, blackouts, holidays.
 * <p>
 * ADR-002: timestamps are stored in UTC ({@link Instant}); opening hours are Vietnam local time
 * ({@code app.zone}). Mọi phép so sánh giờ mở cửa đều đổi Instant sang giờ Việt Nam trước.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarService {

    private final OperatingHoursRepository hoursRepository;
    private final BlackoutRepository blackoutRepository;
    private final HolidayRepository holidayRepository;
    private final LabRepository labRepository;
    private final AuditService auditService;
    private final ZoneId businessZone;
    private final Clock clock;

    /** Hours that apply to a lab: lab-specific rows override the default (labId null) rows. */
    @Transactional(readOnly = true)
    public List<OperatingHours> hoursFor(Integer labId) {
        return hoursRepository.findAllFor(labId);
    }

    @Transactional(readOnly = true)
    public List<Blackout> upcomingBlackouts(Integer labId) {
        return blackoutRepository.findByLabIdAndEndAtAfterOrderByStartAt(labId, Instant.now(clock));
    }

    /**
     * S18 Set opening hours of one weekday, for all labs (labId null) or one lab (override).
     * Open must be before close and both on a 30-minute mark; closed=true means closed all day.
     *
     * @throws BusinessException CALENDAR_DAY_INVALID, CALENDAR_HOURS_INVALID, LAB_NOT_FOUND
     */
    @Transactional
    public OperatingHours setOperatingHours(OperatingHoursRequest request, Long actorId) {
        Integer day = request.dayOfWeek();
        if (day == null || day < 1 || day > 7) {
            throw new BusinessException(ErrorCode.CALENDAR_DAY_INVALID);
        }
        if (!request.closed()) {
            LocalTime open = request.openTime();
            LocalTime close = request.closeTime();
            if (open == null || close == null || !open.isBefore(close) || !onHalfHour(open) || !onHalfHour(close)) {
                throw new BusinessException(ErrorCode.CALENDAR_HOURS_INVALID);
            }
        }
        if (request.labId() != null && !labRepository.existsById(request.labId())) {
            throw new BusinessException(ErrorCode.LAB_NOT_FOUND);
        }
        Optional<OperatingHours> existing = request.labId() == null
                ? hoursRepository.findDefault(day) : hoursRepository.findForLab(request.labId(), day);
        OperatingHours hours = existing.orElseGet(OperatingHours::new);
        String before = existing.map(CalendarService::describe).orElse(null);
        hours.setLabId(request.labId());
        hours.setDayOfWeek(day);
        hours.setClosed(request.closed());
        hours.setOpenTime(request.closed() ? null : request.openTime());
        hours.setCloseTime(request.closed() ? null : request.closeTime());
        hoursRepository.save(hours);
        auditService.record(actorId, "OPERATING_HOURS", (request.labId() == null ? "default" : request.labId()) + "/" + day,
                "SET", before, describe(hours));
        log.info("Operating hours set: labId={}, day={}, {}, by={}", request.labId(), day, describe(hours), actorId);
        return hours;
    }

    /**
     * S18 Add a blackout (lab unavailable). Must end after it starts and end in the future,
     * reason required, and must not overlap another blackout of the same lab (half-open ranges).
     *
     * @throws BusinessException LAB_NOT_FOUND, BLACKOUT_RANGE_INVALID, BLACKOUT_REASON_REQUIRED, BLACKOUT_OVERLAP
     */
    @Transactional
    public Blackout addBlackout(BlackoutRequest request, Long actorId) {
        if (request.labId() == null || !labRepository.existsById(request.labId())) {
            throw new BusinessException(ErrorCode.LAB_NOT_FOUND);
        }
        Instant start = request.startAt();
        Instant end = request.endAt();
        if (start == null || end == null || !start.isBefore(end) || !end.isAfter(Instant.now(clock))) {
            throw new BusinessException(ErrorCode.BLACKOUT_RANGE_INVALID);
        }
        String reason = Texts.clean(request.reason());
        if (reason == null || reason.length() > 255) {
            throw new BusinessException(ErrorCode.BLACKOUT_REASON_REQUIRED);
        }
        if (blackoutRepository.existsOverlap(request.labId(), start, end)) {
            throw new BusinessException(ErrorCode.BLACKOUT_OVERLAP);
        }
        Blackout blackout = new Blackout();
        blackout.setLabId(request.labId());
        blackout.setStartAt(start);
        blackout.setEndAt(end);
        blackout.setReason(reason);
        blackout.setCreatedBy(actorId);
        blackoutRepository.save(blackout);
        auditService.record(actorId, "BLACKOUT", blackout.getId(), "CREATE", null, blackout);
        log.info("Blackout added: id={}, labId={}, {} -> {}, by={}", blackout.getId(), request.labId(), start, end, actorId);
        return blackout;
    }

    /**
     * BR-04 check used by availability and reservation: is the lab open for the WHOLE period?
     * The period must sit inside one local day, that day is not a holiday, the weekday is not closed,
     * start &gt;= open, end &lt;= close, and no blackout overlaps it.
     *
     * @throws BusinessException TIME_RANGE_INVALID, TIME_OUTSIDE_OPENING_HOURS, TIME_BLACKOUT
     */
    @Transactional(readOnly = true)
    public void checkLabOpen(Integer labId, Instant start, Instant end) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new BusinessException(ErrorCode.TIME_RANGE_INVALID);
        }
        ZonedDateTime localStart = start.atZone(businessZone);
        ZonedDateTime localEnd = end.atZone(businessZone);
        LocalDate day = localStart.toLocalDate();
        // Cả khoảng phải nằm trong MỘT ngày giờ Việt Nam (giờ đóng cửa luôn trước nửa đêm)
        if (!localEnd.toLocalDate().equals(day) || holidayRepository.existsById(day)) {
            throw new BusinessException(ErrorCode.TIME_OUTSIDE_OPENING_HOURS);
        }
        int weekday = day.getDayOfWeek().getValue();
        OperatingHours hours = hoursRepository.findForLab(labId, weekday)
                .or(() -> hoursRepository.findDefault(weekday))
                .orElse(null);
        if (hours == null || hours.isClosed()) {
            throw new BusinessException(ErrorCode.TIME_OUTSIDE_OPENING_HOURS);
        }
        // Biên: bắt đầu ĐÚNG giờ mở cửa và kết thúc ĐÚNG giờ đóng cửa đều hợp lệ
        if (localStart.toLocalTime().isBefore(hours.getOpenTime()) || localEnd.toLocalTime().isAfter(hours.getCloseTime())) {
            throw new BusinessException(ErrorCode.TIME_OUTSIDE_OPENING_HOURS);
        }
        if (blackoutRepository.existsOverlap(labId, start, end)) {
            throw new BusinessException(ErrorCode.TIME_BLACKOUT);
        }
    }

    /** Same as {@link #checkLabOpen} but answers true/false (used to filter availability lists). */
    @Transactional(readOnly = true)
    public boolean isLabOpen(Integer labId, Instant start, Instant end) {
        try {
            checkLabOpen(labId, start, end);
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }

    private static boolean onHalfHour(LocalTime t) {
        return (t.getMinute() == 0 || t.getMinute() == 30) && t.getSecond() == 0 && t.getNano() == 0;
    }

    private static String describe(OperatingHours h) {
        return h.isClosed() ? "closed" : h.getOpenTime() + "-" + h.getCloseTime();
    }
}
