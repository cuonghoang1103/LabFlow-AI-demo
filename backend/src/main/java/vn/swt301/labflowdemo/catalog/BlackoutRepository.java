package vn.swt301.labflowdemo.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface BlackoutRepository extends JpaRepository<Blackout, Long> {

    /** Half-open overlap: [s1, e1) and [s2, e2) overlap when s1 &lt; e2 AND s2 &lt; e1 (touching ends do not). */
    @Query("select count(b) > 0 from Blackout b where b.labId = :labId and b.startAt < :end and :start < b.endAt")
    boolean existsOverlap(@Param("labId") Integer labId, @Param("start") Instant start, @Param("end") Instant end);

    List<Blackout> findByLabIdAndEndAtAfterOrderByStartAt(Integer labId, Instant after);
}
