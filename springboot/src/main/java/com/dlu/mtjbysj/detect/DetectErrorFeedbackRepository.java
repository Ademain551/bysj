package com.dlu.mtjbysj.detect;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DetectErrorFeedbackRepository extends JpaRepository<DetectErrorFeedback, Long> {
    List<DetectErrorFeedback> findByFarmerUsernameOrderByCreatedAtDesc(String username);

    List<DetectErrorFeedback> findByExpertUsernameAndStatusOrderByCreatedAtDesc(String username, String status);

    List<DetectErrorFeedback> findByStatusAndAddedToDatasetFalseOrderByCreatedAtDesc(String status);

    List<DetectErrorFeedback> findByDetectResult_IdOrderByCreatedAtDesc(Long detectResultId);

    @Modifying
    @Query("delete from DetectErrorFeedback f where f.detectResult.id in :resultIds")
    int deleteByDetectResultIds(@Param("resultIds") List<Long> resultIds);
}
