package vn.swt301.labflowdemo.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Integer> {

    Optional<Role> findByCode(RoleCode code);

    List<Role> findByCodeIn(Collection<RoleCode> codes);
}
