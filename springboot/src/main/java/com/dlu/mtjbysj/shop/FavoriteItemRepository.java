package com.dlu.mtjbysj.shop;

import com.dlu.mtjbysj.knowledge.Fzwp;
import com.dlu.mtjbysj.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteItemRepository extends JpaRepository<FavoriteItem, Long> {

    List<FavoriteItem> findByUserOrderByCreatedAtDesc(User user);

    Optional<FavoriteItem> findByUserAndItem(User user, Fzwp item);
}
