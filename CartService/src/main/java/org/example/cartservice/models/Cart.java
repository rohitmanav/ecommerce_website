package org.example.cartservice.models;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Setter
@Getter
@Document(collection = "carts")
public class Cart extends BaseModel {
    @Indexed(unique = true)
    private Long userId;

    private List<CartItem> items = new ArrayList<>();

    public Optional<CartItem> findItem(Long productId) {
        for (CartItem item : items) {
            if (item.getProductId().equals(productId)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }
}
