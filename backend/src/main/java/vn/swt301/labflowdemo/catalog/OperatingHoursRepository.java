package vn.swt301.labflowdemo.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OperatingHoursRepository extends JpaRepository<OperatingHours, Integer> {

    @Query("select h from OperatingHours h where h.labId = :labId and h.dayOfWeek = :day")
    Optional<OperatingHours> findForLab(@Param("labId") Integer labId, @Param("day") int day);

    @Query("select h from OperatingHours h where h.labId is null and h.dayOfWeek = :day")
    Optional<OperatingHours> findDefault(@Param("day") int day);

    @Query("select h from OperatingHours h where h.labId is null or h.labId = :labId order by h.dayOfWeek")
    List<OperatingHours> findAllFor(@Param("labId") Integer labId);
}
