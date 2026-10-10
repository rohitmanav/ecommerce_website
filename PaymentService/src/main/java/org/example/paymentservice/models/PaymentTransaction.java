package org.example.paymentservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
public class PaymentTransaction extends BaseModel {
    @ManyToOne
    private Payment payment;

    private String eventType;

    @Column(length = 4000)
    private String details;
}
