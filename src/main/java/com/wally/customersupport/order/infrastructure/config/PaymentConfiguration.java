package com.wally.customersupport.order.infrastructure.config;

import com.wally.customersupport.order.application.port.out.PaymentGateway;
import com.wally.customersupport.order.infrastructure.payment.MercadoPagoPaymentGateway;
import com.wally.customersupport.order.infrastructure.payment.MockPaymentGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class PaymentConfiguration {

    @Bean
    @ConditionalOnProperty(name = "wcs.payment.provider", havingValue = "mercadopago")
    PaymentGateway mercadoPagoPaymentGateway(
            RestClient.Builder restClientBuilder,
            PaymentProperties properties) {
        return new MercadoPagoPaymentGateway(restClientBuilder, properties);
    }

    @Bean
    @ConditionalOnProperty(name = "wcs.payment.provider", havingValue = "mock", matchIfMissing = true)
    @ConditionalOnMissingBean(PaymentGateway.class)
    PaymentGateway mockPaymentGateway(PaymentProperties properties) {
        return new MockPaymentGateway(properties);
    }
}
