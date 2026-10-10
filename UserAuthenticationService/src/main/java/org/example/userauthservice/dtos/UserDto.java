package org.example.userauthservice.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class UserDto {
    private Long id;
    private String name;
    private String email;
    private String phoneNumber;
    private String authProvider;
    private List<String> roles = new ArrayList<>();
}
