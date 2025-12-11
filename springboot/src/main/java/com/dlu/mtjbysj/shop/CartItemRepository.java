package com.dlu.mtjbysj.shop;

import com.dlu.mtjbysj.knowledge.Fzwp;
import com.dlu.mtjbysj.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByUserOrderByCreatedAtDesc(User user);

    Optional<CartItem> findByUserAndItem(User user, Fzwp item);
}
