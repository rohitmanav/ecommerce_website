package org.example.orderservice.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AddressDto {
    @NotBlank
    private String fullName;

    @NotBlank
    @Pattern(regexp = "^[0-9+\\- ]{7,15}$", message = "phone number must have 7-15 digits")
    private String phoneNumber;

    @NotBlank
    private String line1;

    private String line2;

    @NotBlank
    private String city;

    @NotBlank
    private String state;

    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9 \\-]{3,10}$", message = "postal code is not valid")
    private String postalCode;

    private String country = "India";
}
