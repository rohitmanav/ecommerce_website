package org.example.orderservice.models;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity(name = "order_items")
public class OrderItem extends BaseModel {
    @ManyToOne
    private Order order;

    private Long productId;
    private String productName;
    private String imageUrl;
    private Double unitPrice;
    private Integer quantity;
}
