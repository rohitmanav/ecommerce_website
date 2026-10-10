package org.example.orderservice.repos;

import org.example.orderservice.models.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepo extends JpaRepository<Order,Long> {
    List<Order> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
