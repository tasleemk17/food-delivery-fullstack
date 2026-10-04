import {
  buildOrderFromCart,
  PricingError,
  orderTotalPaise,
} from "../services/pricing.js";

const foods = [
  { _id: "f1", name: "Greek Salad", price: 120 },
  { _id: "f2", name: "Veg Roll", price: 90 },
];

describe("buildOrderFromCart", () => {
  test("uses database prices and adds the delivery charge", () => {
    const result = buildOrderFromCart({ f1: 2, f2: 1 }, foods);
    expect(result.subtotal).toBe(330);
    expect(result.amount).toBe(380);
    expect(result.items).toEqual([
      { _id: "f1", name: "Greek Salad", price: 120, quantity: 2 },
      { _id: "f2", name: "Veg Roll", price: 90, quantity: 1 },
    ]);
  });

  test("skips items whose quantity is 0", () => {
    const result = buildOrderFromCart({ f1: 1, f2: 0 }, foods);
    expect(result.items).toHaveLength(1);
  });

  test("rejects an empty cart", () => {
    expect(() => buildOrderFromCart({}, foods)).toThrow("Your cart is empty");
    expect(() => buildOrderFromCart({ f1: 0 }, foods)).toThrow(PricingError);
  });

  test("rejects an item that no longer exists", () => {
    expect(() => buildOrderFromCart({ deleted: 1 }, foods)).toThrow(
      "no longer available",
    );
  });

  test("rejects fractional or huge quantities", () => {
    expect(() => buildOrderFromCart({ f1: 1.5 }, foods)).toThrow(
      "Invalid quantity",
    );
    expect(() => buildOrderFromCart({ f1: 1000 }, foods)).toThrow(
      "Invalid quantity",
    );
  });
});

describe("orderTotalPaise", () => {
  test("matches how Stripe adds rounded line items", () => {
    const items = [{ price: 99.99, quantity: 3 }];
    // 9999 * 3 + 5000 delivery
    expect(orderTotalPaise(items)).toBe(34997);
  });
});
