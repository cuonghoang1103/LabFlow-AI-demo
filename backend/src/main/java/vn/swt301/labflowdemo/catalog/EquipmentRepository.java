package vn.swt301.labflowdemo.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    Optional<Equipment> findBySerialIgnoreCase(String serial);

    /** S16: search name/serial, filter lab/type/status - paged on the server. */
    @Query("""
            select e from Equipment e
            where (:q is null or lower(e.serial) like lower(concat('%', :q, '%'))
                              or lower(e.name) like lower(concat('%', :q, '%')))
              and (:labId is null or e.labId = :labId)
              and (:typeId is null or e.typeId = :typeId)
              and (:status is null or e.status = :status)
            """)
    Page<Equipment> search(@Param("q") String q, @Param("labId") Integer labId, @Param("typeId") Integer typeId,
                           @Param("status") EquipmentStatus status, Pageable pageable);

    List<Equipment> findByTypeIdAndStatusOrderBySerial(Integer typeId, EquipmentStatus status);
}
