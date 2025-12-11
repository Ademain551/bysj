package com.dlu.mtjbysj.guide;

import com.dlu.mtjbysj.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GuideArticleFavoriteRepository extends JpaRepository<GuideArticleFavorite, Long> {

    List<GuideArticleFavorite> findByUserOrderByCreatedAtDesc(User user);

    Optional<GuideArticleFavorite> findByUserAndArticle(User user, GuideArticle article);
}
