package org.example.orderservice.models;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Setter
@Getter
@Entity(name = "orders")
public class Order extends BaseModel {
    private Long userId;
    private String userEmail;
    private String userName;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private Double totalAmount;
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @Embedded
    private Address deliveryAddress;

    private Long paymentId;

    @Column(length = 1000)
    private String paymentLink;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @OrderBy("changedAt ASC")
    private List<OrderStatusHistory> statusHistory = new ArrayList<>();

    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }

    public void changeStatus(OrderStatus newStatus, String note) {
        this.status = newStatus;
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(this);
        history.setStatus(newStatus);
        history.setNote(note);
        history.setChangedAt(new Date());
        statusHistory.add(history);
    }
}
