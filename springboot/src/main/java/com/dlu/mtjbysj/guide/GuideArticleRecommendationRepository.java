package com.dlu.mtjbysj.guide;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuideArticleRecommendationRepository extends JpaRepository<GuideArticleRecommendation, Long> {

    List<GuideArticleRecommendation> findByArticleId(Long articleId);

    void deleteAllByArticleId(Long articleId);
}
