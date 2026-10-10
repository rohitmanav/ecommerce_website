package org.example.paymentservice.paymentgateway;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentLinkDto {
    private String url;
    private String gatewayReferenceId;
}
