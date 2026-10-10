package org.example.userauthservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Setter
@Getter
@Entity
public class PasswordResetToken extends BaseModel {
    @Column(unique = true, nullable = false)
    private String token;

    private Date expiresAt;

    private Boolean used = false;

    @ManyToOne
    private User user;
}
