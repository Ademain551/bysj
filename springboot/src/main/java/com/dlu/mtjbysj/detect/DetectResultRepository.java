package com.dlu.mtjbysj.detect;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DetectResultRepository extends JpaRepository<DetectResult, Long> {
    Page<DetectResult> findByUserUsernameOrderByCreatedAtDesc(String username, Pageable pageable);
    Page<DetectResult> findByUserUsernameAndModelLabelContainingIgnoreCaseOrderByCreatedAtDesc(String username, String modelLabel, Pageable pageable);
    Page<DetectResult> findByModelLabelContainingIgnoreCaseOrderByCreatedAtDesc(String modelLabel, Pageable pageable);
    boolean existsByUser_Id(Long userId);
    @Modifying
    long deleteByUser_Id(Long userId);

    @Query(value = "SELECT DATE_FORMAT(created_at, '%Y-%m') AS ym, COUNT(*) AS cnt " +
            "FROM detect_results WHERE username = :username " +
            "GROUP BY DATE_FORMAT(created_at, '%Y-%m') ORDER BY ym", nativeQuery = true)
    List<Object[]> countMonthlyByUsername(@Param("username") String username);

    @Query(value = "SELECT COALESCE(dr.predicted_class, dr.model_label) AS name, COUNT(*) AS cnt " +
            "FROM detect_results dr " +
            "WHERE dr.username = :username " +
            "GROUP BY COALESCE(dr.predicted_class, dr.model_label) ORDER BY cnt DESC", nativeQuery = true)
    List<Object[]> countByTypeForUser(@Param("username") String username);

    Page<DetectResult> findByUserUsernameAndModelLabelContainingIgnoreCase(String username, String modelLabel, Pageable pageable);
}