package vn.swt301.labflowdemo.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LabRepository extends JpaRepository<Lab, Integer> {

    Optional<Lab> findByCodeIgnoreCase(String code);

    /** S13: search code/name, filter building/status/min capacity - paged on the server. */
    @Query("""
            select l from Lab l
            where (:q is null or lower(l.code) like lower(concat('%', :q, '%'))
                              or lower(l.name) like lower(concat('%', :q, '%')))
              and (:buildingId is null or l.buildingId = :buildingId)
              and (:status is null or l.status = :status)
              and (:minCapacity is null or l.capacity >= :minCapacity)
            """)
    Page<Lab> search(@Param("q") String q, @Param("buildingId") Integer buildingId,
                     @Param("status") ActiveStatus status, @Param("minCapacity") Integer minCapacity, Pageable pageable);

    /** Active labs in active buildings with enough seats - candidates for availability. */
    @Query("""
            select l from Lab l, Building b
            where b.id = l.buildingId and l.status = vn.swt301.labflowdemo.catalog.ActiveStatus.ACTIVE
              and b.status = vn.swt301.labflowdemo.catalog.ActiveStatus.ACTIVE
              and l.capacity >= :minCapacity and (:buildingId is null or l.buildingId = :buildingId)
            order by l.code
            """)
    List<Lab> findBookable(@Param("minCapacity") int minCapacity, @Param("buildingId") Integer buildingId);
}
