package org.example.orderservice.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Setter
@Getter
public class OrderTrackingDto {
    private Long orderId;
    private String currentStatus;
    private List<StatusUpdateDto> history = new ArrayList<>();

    @Setter
    @Getter
    public static class StatusUpdateDto {
        private String status;
        private String note;
        private Date changedAt;
    }
}
