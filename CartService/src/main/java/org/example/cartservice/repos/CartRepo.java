package org.example.cartservice.repos;

import org.example.cartservice.models.Cart;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepo extends MongoRepository<Cart,String> {
    Optional<Cart> findByUserId(Long userId);
}
