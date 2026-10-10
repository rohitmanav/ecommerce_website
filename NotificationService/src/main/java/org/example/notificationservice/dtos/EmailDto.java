package org.example.notificationservice.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmailDto {
    @NotBlank
    @Email
    private String to;

    private String from;

    @NotBlank
    private String subject;

    @NotBlank
    private String body;
}
