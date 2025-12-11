package com.dlu.mtjbysj.knowledge;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DiseaseRepository extends JpaRepository<Disease, Long> {
    Optional<Disease> findByModelLabel(String modelLabel);
    List<Disease> findAllByOrderByPlantAscNameAsc();
    Page<Disease> findByNameContainingIgnoreCaseOrderByUpdatedAtDesc(String name, Pageable pageable);
}