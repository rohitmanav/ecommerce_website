package org.example.paymentservice.dtos;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

// Returned by the user service's /auth/validateToken endpoint.
@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserDto {
    private Long userId;
    private String name;
    private String email;
    private List<String> roles = new ArrayList<>();

    @JsonIgnore
    public boolean isAdmin() {
        return roles.contains("ADMIN");
    }
}
