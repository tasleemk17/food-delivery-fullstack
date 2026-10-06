package com.fooddelivery.orders.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fooddelivery.orders.domain.Address;
import com.fooddelivery.orders.domain.OrderEntity;
import com.fooddelivery.orders.domain.OrderItemEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * An order as JSON, with the same field names the React apps already read
 * from the Node backend (_id, items, amount, address, status, payment, ...).
 * Entities are never returned directly, so the database layout can change
 * without breaking the API.
 */
public record OrderResponse(
        @JsonProperty("_id") String id,
        String userId,
        List<Item> items,
        BigDecimal amount,
        AddressResponse address,
        String status,
        String paymentMethod,
        boolean payment,
        Instant date) {

    public record Item(@JsonProperty("_id") String foodId, String name, BigDecimal price, int quantity) {

        static Item from(OrderItemEntity item) {
            return new Item(item.getFoodId(), item.getName(), plain(item.getUnitPrice()), item.getQuantity());
        }
    }

    public record AddressResponse(String firstName, String lastName, String email, String street, String city,
                                  String state, String zipcode, String country, String phone) {

        static AddressResponse from(Address a) {
            return new AddressResponse(a.getFirstName(), a.getLastName(), a.getEmail(), a.getStreet(),
                    a.getCity(), a.getState(), a.getZipcode(), a.getCountry(), a.getPhone());
        }
    }

    public static OrderResponse from(OrderEntity order) {
        return new OrderResponse(
                order.getId() == null ? null : order.getId().toString(),
                order.getUserId(),
                order.getItems().stream().map(Item::from).toList(),
                plain(order.getAmount()),
                AddressResponse.from(order.getAddress()),
                order.getStatus().label(),
                order.getPaymentMethod().apiValue(),
                order.isPaid(),
                order.getCreatedAt());
    }

    /** 380.00 -> 380 and 99.50 -> 99.5, like the numbers Node returns. */
    private static BigDecimal plain(BigDecimal value) {
        return value.stripTrailingZeros();
    }
}
