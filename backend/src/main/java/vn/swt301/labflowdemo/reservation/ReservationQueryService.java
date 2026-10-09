package vn.swt301.labflowdemo.reservation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Read-only door of the reservation module for other modules (availability).
 * Luật module: module khác không đọc thẳng bảng reservation_items - phải đi qua service này.
 */
@Service
@RequiredArgsConstructor
public class ReservationQueryService {

    private final ReservationItemRepository itemRepository;

    @Transactional(readOnly = true)
    public Set<Integer> busyLabIds(Instant start, Instant end) {
        return new HashSet<>(itemRepository.busyLabIds(start, end));
    }

    @Transactional(readOnly = true)
    public Set<Long> busyEquipmentIds(Instant start, Instant end) {
        return new HashSet<>(itemRepository.busyEquipmentIds(start, end));
    }
}
