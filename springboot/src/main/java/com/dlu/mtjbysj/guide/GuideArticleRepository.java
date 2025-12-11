package com.dlu.mtjbysj.guide;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuideArticleRepository extends JpaRepository<GuideArticle, Long> {

    Page<GuideArticle> findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(String title,
                                                                                    String content,
                                                                                    Pageable pageable);
}
