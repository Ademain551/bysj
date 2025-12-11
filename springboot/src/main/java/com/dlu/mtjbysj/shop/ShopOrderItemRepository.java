package com.dlu.mtjbysj.shop;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShopOrderItemRepository extends JpaRepository<ShopOrderItem, Long> {

    List<ShopOrderItem> findByOrder(ShopOrder order);
}
