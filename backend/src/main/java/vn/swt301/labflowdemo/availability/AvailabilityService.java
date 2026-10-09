package vn.swt301.labflowdemo.availability;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.swt301.labflowdemo.catalog.BookingPolicy;
import vn.swt301.labflowdemo.catalog.CalendarService;
import vn.swt301.labflowdemo.catalog.Equipment;
import vn.swt301.labflowdemo.catalog.EquipmentRepository;
import vn.swt301.labflowdemo.catalog.EquipmentStatus;
import vn.swt301.labflowdemo.catalog.Lab;
import vn.swt301.labflowdemo.catalog.LabRepository;
import vn.swt301.labflowdemo.common.BusinessException;
import vn.swt301.labflowdemo.common.ErrorCode;
import vn.swt301.labflowdemo.reservation.ReservationQueryService;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * "What is free?" (E3 day 24, S20 - owner C2). A lab is available for [from, to) when it is ACTIVE
 * (and its building too), has enough seats, is open for the whole period, has no blackout, and no
 * active reservation overlaps (half-open: a booking ending at 10:00 does not block 10:00-11:00).
 */
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    static final int MAX_CAPACITY_FILTER = 200;

    private final LabRepository labRepository;
    private final EquipmentRepository equipmentRepository;
    private final CalendarService calendarService;
    private final BookingPolicy bookingPolicy;
    private final ReservationQueryService reservationQuery;

    /**
     * @param minCapacity seats needed, 1-200 (null = 1)
     * @param buildingId  optional building filter
     * @throws BusinessException TIME_RANGE_INVALID, TIME_SLOT_MISALIGNED, TIME_IN_PAST, TIME_TOO_FAR_AHEAD,
     *                           VALIDATION_ERROR (capacity out of range)
     */
    @Transactional(readOnly = true)
    public List<Lab> findAvailableLabs(Instant from, Instant to, Integer minCapacity, Integer buildingId) {
        bookingPolicy.checkPeriod(from, to);
        int seats = minCapacity == null ? 1 : minCapacity;
        if (seats < 1 || seats > MAX_CAPACITY_FILTER) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Capacity must be between 1 and 200");
        }
        Set<Integer> busy = reservationQuery.busyLabIds(from, to);
        return labRepository.findBookable(seats, buildingId).stream()
                .filter(lab -> !busy.contains(lab.getId()))
                .filter(lab -> calendarService.isLabOpen(lab.getId(), from, to))
                .toList();
    }

    /**
     * Free units of one equipment type for [from, to): status AVAILABLE, its lab open, not reserved.
     *
     * @throws BusinessException the period errors, EQUIPMENT_TYPE_NOT_FOUND (typeId null)
     */
    @Transactional(readOnly = true)
    public List<Equipment> findAvailableEquipment(Instant from, Instant to, Integer typeId) {
        bookingPolicy.checkPeriod(from, to);
        if (typeId == null) {
            throw new BusinessException(ErrorCode.EQUIPMENT_TYPE_NOT_FOUND);
        }
        Set<Long> busy = reservationQuery.busyEquipmentIds(from, to);
        return equipmentRepository.findByTypeIdAndStatusOrderBySerial(typeId, EquipmentStatus.AVAILABLE).stream()
                .filter(e -> !busy.contains(e.getId()))
                .filter(e -> calendarService.isLabOpen(e.getLabId(), from, to))
                .toList();
    }
}
