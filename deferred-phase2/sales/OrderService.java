package com.cassierq.api.sales;

import com.cassierq.api.common.PageResponse;
import com.cassierq.api.common.exception.BadRequestException;
import com.cassierq.api.common.exception.ResourceNotFoundException;
import com.cassierq.api.domain.entity.Customer;
import com.cassierq.api.domain.entity.Order;
import com.cassierq.api.domain.entity.OrderItem;
import com.cassierq.api.domain.entity.OrderStatus;
import com.cassierq.api.domain.entity.Product;
import com.cassierq.api.domain.repository.CustomerRepository;
import com.cassierq.api.domain.repository.OrderRepository;
import com.cassierq.api.domain.repository.ProductRepository;
import com.cassierq.api.domain.repository.StoreRepository;
import com.cassierq.api.domain.repository.UserRepository;
import com.cassierq.api.sales.dto.CreateOrderItemRequest;
import com.cassierq.api.sales.dto.CreateOrderRequest;
import com.cassierq.api.sales.dto.OrderResponse;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;

    @Transactional
    public OrderResponse create(UUID storeId, UUID cashierId, CreateOrderRequest request) {
        Order order = Order.builder()
                .store(storeRepository.getReferenceById(storeId))
                .cashier(userRepository.getReferenceById(cashierId))
                .customer(resolveCustomer(storeId, request.customerId()))
                .status(OrderStatus.PAID)
                .paymentMethod(request.paymentMethod())
                .discount(request.discount() != null ? request.discount() : BigDecimal.ZERO)
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CreateOrderItemRequest itemRequest : request.items()) {
            Product product = productRepository.findByIdAndStoreId(itemRequest.productId(), storeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Produk tidak ditemukan: " + itemRequest.productId()));

            if (product.getStock() < itemRequest.quantity()) {
                throw new BadRequestException("Stok tidak cukup untuk produk " + product.getName());
            }
            product.setStock(product.getStock() - itemRequest.quantity());
            productRepository.save(product);

            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(itemRequest.quantity()));
            subtotal = subtotal.add(lineTotal);

            order.addItem(OrderItem.builder()
                    .product(product)
                    .productName(product.getName())
                    .unitPrice(product.getPrice())
                    .quantity(itemRequest.quantity())
                    .lineTotal(lineTotal)
                    .build());
        }

        order.setSubtotal(subtotal);
        BigDecimal total = subtotal.subtract(order.getDiscount());
        if (total.signum() < 0) {
            throw new BadRequestException("Diskon tidak boleh lebih besar dari subtotal");
        }
        order.setTotal(total);

        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> list(UUID storeId, Pageable pageable) {
        Page<Order> page = orderRepository.findByStoreIdOrderByCreatedAtDesc(storeId, pageable);
        return PageResponse.of(page.map(OrderResponse::from));
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID storeId, UUID orderId) {
        return OrderResponse.from(findOwned(storeId, orderId));
    }

    @Transactional
    public OrderResponse voidOrder(UUID storeId, UUID orderId) {
        Order order = findOwned(storeId, orderId);
        if (order.getStatus() != OrderStatus.PAID) {
            throw new BadRequestException("Hanya order berstatus PAID yang bisa dibatalkan");
        }

        // Restock every line item before flipping the order status, both
        // inside the same transaction so a failure rolls back cleanly.
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            product.setStock(product.getStock() + item.getQuantity());
            productRepository.save(product);
        }

        order.setStatus(OrderStatus.CANCELLED);
        return OrderResponse.from(orderRepository.save(order));
    }

    private Order findOwned(UUID storeId, UUID orderId) {
        return orderRepository.findByIdAndStoreId(orderId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Order tidak ditemukan"));
    }

    private Customer resolveCustomer(UUID storeId, UUID customerId) {
        if (customerId == null) return null;
        return customerRepository.findById(customerId)
                .filter(c -> c.getStore().getId().equals(storeId))
                .orElseThrow(() -> new ResourceNotFoundException("Customer tidak ditemukan"));
    }
}
