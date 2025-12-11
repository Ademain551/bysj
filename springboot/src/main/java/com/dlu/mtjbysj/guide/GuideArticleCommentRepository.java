package com.dlu.mtjbysj.guide;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuideArticleCommentRepository extends JpaRepository<GuideArticleComment, Long> {

    List<GuideArticleComment> findByArticleIdOrderByCreatedAtAsc(Long articleId);

    void deleteAllByArticleId(Long articleId);

    long countByArticleId(Long articleId);
}
