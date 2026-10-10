package org.example.userauthservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Setter
@Getter
@Entity
@Table(indexes = @Index(columnList = "userId"))
public class UserOrderActivity extends BaseModel {
    @Column(unique = true, nullable = false)
    private Long orderId;

    private Long userId;
    private String orderStatus;
    private Double totalAmount;
    private String currency;
    private Date statusChangedAt;
}
