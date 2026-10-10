package org.example.orderservice.dtos;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class SmsDto {
    private String to;
    private String message;
}
