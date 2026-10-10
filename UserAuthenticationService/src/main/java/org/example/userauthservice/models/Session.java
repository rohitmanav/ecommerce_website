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
public class Session extends BaseModel {
    @Column(length = 2048)
    private String token;

    private Date expiresAt;

    @Column(unique = true)
    private String refreshToken;

    private Date refreshTokenExpiresAt;

    @ManyToOne
    private User user;
}

//1             1
//Session      User
//M             1
//
//M :  1
