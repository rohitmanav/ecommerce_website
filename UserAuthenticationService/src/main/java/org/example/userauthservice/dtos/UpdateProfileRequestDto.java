package org.example.userauthservice.dtos;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class UpdateProfileRequestDto {
    @Size(min = 1, max = 100)
    private String name;

    @Pattern(regexp = "^[0-9+\\- ]{7,15}$", message = "phone number must have 7-15 digits")
    private String phoneNumber;
}
