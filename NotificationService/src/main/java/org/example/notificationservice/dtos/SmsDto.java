package org.example.notificationservice.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SmsDto {
    @NotBlank
    @Pattern(regexp = "^\\+?[0-9 ()-]{8,20}$", message = "must be a phone number")
    private String to;

    @NotBlank
    @Size(max = 1600)
    private String message;
}
