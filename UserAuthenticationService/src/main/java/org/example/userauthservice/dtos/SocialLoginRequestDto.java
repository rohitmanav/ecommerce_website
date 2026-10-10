package org.example.userauthservice.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class SocialLoginRequestDto {
    // Only GOOGLE is supported.
    @NotBlank
    private String provider;

    @NotBlank
    private String idToken;
}
