package org.example.orderservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Embeddable
public class Address {
    private String fullName;
    private String phoneNumber;
    private String line1;
    private String line2;
    private String city;
    // BaseModel already maps a "state" column on the owning order.
    @Column(name = "address_state")
    private String state;
    private String postalCode;
    private String country;
}
