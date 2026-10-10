package org.example.orderservice.dtos;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class EmailDto {
    private String to;
    private String from;
    private String subject;
    private String body;
}
