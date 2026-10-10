package org.example.userauthservice.repos;

import org.example.userauthservice.models.UserOrderActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserOrderActivityRepo extends JpaRepository<UserOrderActivity,Long> {
    Optional<UserOrderActivity> findByOrderId(Long orderId);

    List<UserOrderActivity> findAllByUserIdOrderByStatusChangedAtDesc(Long userId);
}
