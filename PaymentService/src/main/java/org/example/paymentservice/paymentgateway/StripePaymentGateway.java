package org.example.paymentservice.paymentgateway;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentLink;
import com.stripe.model.Price;
import com.stripe.param.PaymentLinkCreateParams;
import com.stripe.param.PriceCreateParams;
import org.example.paymentservice.exceptions.PaymentGatewayException;
import org.example.paymentservice.models.Payment;
import org.example.paymentservice.models.PaymentGatewayType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StripePaymentGateway implements IPaymentGateway {

    @Value("${stripe.apikey}")
    private String stripeApiKey;

    @Value("${app.payment.callback-base-url}")
    private String callbackBaseUrl;

    @Override
    public PaymentLinkDto createPaymentLink(Payment payment) {
        try {
            Stripe.apiKey = this.stripeApiKey;

            Price price = getPrice(payment);
            PaymentLinkCreateParams params =
                    PaymentLinkCreateParams.builder()
                            .addLineItem(
                                    PaymentLinkCreateParams.LineItem.builder()
                                            .setPrice(price.getId())
                                            .setQuantity(1L)
                                            .build()
                            )
                            .putMetadata("order_id", String.valueOf(payment.getOrderId()))
                            .putMetadata("payment_id", String.valueOf(payment.getId()))
                            .setAfterCompletion(PaymentLinkCreateParams.AfterCompletion.builder()
                                    .setType(PaymentLinkCreateParams.AfterCompletion.Type.REDIRECT)
                                    .setRedirect(PaymentLinkCreateParams.AfterCompletion.Redirect.builder()
                                            .setUrl(callbackBaseUrl + "/payments/callback/stripe?payment_id=" + payment.getId())
                                            .build())
                                    .build())
                            .build();
            PaymentLink paymentLink = PaymentLink.create(params);
            return new PaymentLinkDto(paymentLink.getUrl(), paymentLink.getId());
        } catch (StripeException exception) {
            throw new PaymentGatewayException("Stripe could not create the payment link: " + exception.getMessage());
        }
    }

    private Price getPrice(Payment payment) throws StripeException {
        PriceCreateParams params =
                PriceCreateParams.builder()
                        .setCurrency(payment.getCurrency().toLowerCase())
                        .setUnitAmount(payment.getAmount())
                        .setProductData(
                                PriceCreateParams.ProductData.builder().setName("Order #" + payment.getOrderId()).build()
                        )
                        .build();
        return Price.create(params);
    }

    @Override
    public PaymentGatewayType getType() {
        return PaymentGatewayType.STRIPE;
    }
}
