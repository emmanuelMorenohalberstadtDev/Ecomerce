package com.ecommerce.payment.infrastructure.persistence;

import com.ecommerce.payment.domain.port.out.PaymentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Persistence-layer wiring for the payment context.
 *
 * <p>Resides in the {@code persistence} package to access the package-private Spring Data DAO
 * interfaces without making them public — mirrors {@code OrderPersistenceConfiguration}/
 * {@code CheckoutPersistenceConfiguration} exactly.
 */
@Configuration
public class PaymentPersistenceConfiguration {

    @Bean
    public PaymentRepository paymentRepository(SpringDataPaymentDao springDataPaymentDao,
                                               SpringDataPaymentAttemptDao springDataPaymentAttemptDao,
                                               SpringDataRefundDao springDataRefundDao) {
        return new JpaPaymentRepository(springDataPaymentDao, springDataPaymentAttemptDao, springDataRefundDao);
    }
}
