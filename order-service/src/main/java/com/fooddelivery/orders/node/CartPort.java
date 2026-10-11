package com.fooddelivery.orders.node;

/** What order-service needs from the Node backend, which still owns users, carts and the menu. */
public interface CartPort {

    /** The user's saved cart with current food names and prices. */
    CartSnapshot fetchCart(String userId);

    /** Empties the user's cart (after an order is placed or paid). */
    void clearCart(String userId);

    /** Drops cart entries whose food was deleted from the menu. */
    void removeUnavailable(String userId);
}
