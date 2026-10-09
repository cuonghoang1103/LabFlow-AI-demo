package vn.swt301.labflowdemo.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildingRepository extends JpaRepository<Building, Integer> {

    boolean existsByCodeIgnoreCase(String code);
}
