package com.rbdip.bookstore.order;

import com.rbdip.bookstore.product.Product;
import com.rbdip.bookstore.product.ProductRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Загружает товары и проверяет количество до сохранения заказа.
 */
@Component
public class OrderItemPreparer {

    private final ProductRepository productRepository;

    public OrderItemPreparer(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<PreparedOrderItem> prepare(List<CreateOrderRequest.Item> items) {
        return items.stream().map(this::prepareItem).toList();
    }

    private PreparedOrderItem prepareItem(CreateOrderRequest.Item item) {
        Product product = productRepository.findById(item.productId())
                .orElseThrow(() -> new IllegalArgumentException("product " + item.productId() + " not found"));
        int quantity = item.quantity() == null ? 1 : item.quantity();
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        return new PreparedOrderItem(product, quantity);
    }
}
