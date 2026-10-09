package vn.swt301.labflowdemo.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** S10: search by email/name (q = "" matches everything) + filter by role and status, paged and sorted on the server. */
    @Query("""
            select distinct u from User u left join u.roles r
            where (lower(u.email) like lower(concat('%', :q, '%'))
                              or lower(u.fullName) like lower(concat('%', :q, '%')))
              and (:role is null or r.code = :role)
              and (:status is null or u.status = :status)
            """)
    Page<User> search(@Param("q") String q, @Param("role") RoleCode role, @Param("status") UserStatus status,
                      Pageable pageable);

    /** Used to keep at least one active admin (USER_LAST_ADMIN). */
    @Query("select count(distinct u) from User u join u.roles r where r.code = vn.swt301.labflowdemo.user.RoleCode.ADMIN and u.status = vn.swt301.labflowdemo.user.UserStatus.ACTIVE")
    long countActiveAdmins();
}
