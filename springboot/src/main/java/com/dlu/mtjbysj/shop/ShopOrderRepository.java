package com.dlu.mtjbysj.shop;

import com.dlu.mtjbysj.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShopOrderRepository extends JpaRepository<ShopOrder, Long> {

    List<ShopOrder> findByUserOrderByCreatedAtDesc(User user);
}
