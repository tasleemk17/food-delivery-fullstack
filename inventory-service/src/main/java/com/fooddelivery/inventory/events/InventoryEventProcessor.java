package com.fooddelivery.inventory.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.inventory.service.InventoryException;
import com.fooddelivery.inventory.service.InventoryService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Turns an order event into a change to the reserved stock.
 *
 * | Event                       | What happens                                        |
 * |-----------------------------|-----------------------------------------------------|
 * | ORDER_PAID                  | commit: the stock stays taken                       |
 * | ORDER_PLACED, cash on delivery | commit: nothing to wait for                      |
 * | ORDER_PAYMENT_FAILED        | release: the stock goes back                        |
 * | anything else               | ignored (a new event type must not break this service) |
 *
 * Both commit and release are safe to repeat, so it does not matter that Kafka may deliver an
 * event twice, or that the whole topic is read again. An order with no reservation (all its
 * foods are untracked) is simply a no-op.
 */
@Service
public class InventoryEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventProcessor.class);

    /** What the event led to. */
    public enum Result { COMMITTED, RELEASED, IGNORED }

    private final ObjectMapper mapper;
    private final InventoryService inventory;

    public InventoryEventProcessor(ObjectMapper mapper, InventoryService inventory) {
        this.mapper = mapper;
        this.inventory = inventory;
    }

    public Result process(String json) {
        OrderEvent event = parse(json);
        UUID orderId = event.order().orderId();

        Result result = switch (event.type()) {
            case "ORDER_PAID" -> Result.COMMITTED;
            case "ORDER_PLACED" -> "COD".equals(event.order().paymentMethod()) ? Result.COMMITTED : Result.IGNORED;
            case "ORDER_PAYMENT_FAILED" -> Result.RELEASED;
            default -> Result.IGNORED;
        };
        if (result == Result.IGNORED) {
            return result;
        }

        String outcome;
        try {
            outcome = (result == Result.COMMITTED ? inventory.commit(orderId) : inventory.release(orderId)).status();
        } catch (InventoryException e) {
            if (e.status() == HttpStatus.CONFLICT) {
                // For example: the payment came in after the stock was already given back. Retrying
                // cannot fix that and a person must decide, so it is parked, loudly.
                log.error("{} of order {} contradicts its reservation: {}", event.type(), orderId, e.getMessage());
                throw new UnprocessableEventException(event.type() + " of order " + orderId + ": " + e.getMessage(), e);
            }
            throw e;
        }
        // NOT_TRACKED: the order held no tracked food, so there was no reservation to change
        log.info("{} of order {}: reservation is {}", event.type(), orderId, outcome);
        return result;
    }

    private OrderEvent parse(String json) {
        OrderEvent event;
        try {
            event = mapper.readValue(json, OrderEvent.class);
        } catch (JsonProcessingException | RuntimeException e) {
            throw new UnprocessableEventException("Not a valid order event", e);
        }
        if (event == null || event.type() == null || event.order() == null || event.order().orderId() == null) {
            throw new UnprocessableEventException("The order event has no type or order id");
        }
        return event;
    }
}
