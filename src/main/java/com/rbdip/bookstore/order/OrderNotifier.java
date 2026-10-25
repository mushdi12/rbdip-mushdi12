package com.rbdip.bookstore.order;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Имитирует отправку подтверждения заказа. Реальный почтовый транспорт не настроен.
 */
@Component
public class OrderNotifier {

    public void sendConfirmation(String customerName, Long orderId, BigDecimal total) {
        System.out.printf(
                "[email] Dear %s, your order #%d for %s has been placed.%n", customerName, orderId, total);
    }
}
