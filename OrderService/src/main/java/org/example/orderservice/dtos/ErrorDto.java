package org.example.orderservice.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Setter
@Getter
public class ErrorDto {
    private int status;
    private String message;
    private Date timestamp = new Date();

    public ErrorDto(int status, String message) {
        this.status = status;
        this.message = message;
    }
}
