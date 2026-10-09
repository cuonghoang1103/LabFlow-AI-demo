package vn.swt301.labflowdemo.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ReservationItemRepository extends JpaRepository<ReservationItem, Long> {

    /** Half-open overlap on the same lab among ACTIVE items: s1 &lt; e2 AND s2 &lt; e1. */
    @Query("""
            select count(i) > 0 from ReservationItem i
            where i.active = true and i.labId = :labId and i.startAt < :end and :start < i.endAt
            """)
    boolean existsLabOverlap(@Param("labId") Integer labId, @Param("start") Instant start, @Param("end") Instant end);

    @Query("""
            select distinct i.labId from ReservationItem i
            where i.active = true and i.labId is not null and i.startAt < :end and :start < i.endAt
            """)
    List<Integer> busyLabIds(@Param("start") Instant start, @Param("end") Instant end);

    @Query("""
            select distinct i.equipmentId from ReservationItem i
            where i.active = true and i.equipmentId is not null and i.startAt < :end and :start < i.endAt
            """)
    List<Long> busyEquipmentIds(@Param("start") Instant start, @Param("end") Instant end);

    List<ReservationItem> findByReservationId(Long reservationId);
}
