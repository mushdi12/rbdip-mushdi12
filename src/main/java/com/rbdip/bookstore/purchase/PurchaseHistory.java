package com.rbdip.bookstore.purchase;

/**
 * Публичный контракт проверки покупки без доступа к внутренним классам заказов.
 */
public interface PurchaseHistory {

    boolean hasPurchased(Long productId, String customerName);
}
