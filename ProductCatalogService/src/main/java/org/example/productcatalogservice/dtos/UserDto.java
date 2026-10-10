package org.example.productcatalogservice.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class UserDto {
    private Long userId;
    private String name;
    private String email;
    private List<String> roles = new ArrayList<>();

    public boolean isAdmin() {
        return roles.contains("ADMIN");
    }
}
