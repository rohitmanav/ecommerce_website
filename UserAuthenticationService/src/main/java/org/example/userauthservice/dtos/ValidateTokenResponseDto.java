package org.example.userauthservice.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class ValidateTokenResponseDto {
    private Long userId;
    private String email;
    private String name;
    private List<String> roles = new ArrayList<>();
}
