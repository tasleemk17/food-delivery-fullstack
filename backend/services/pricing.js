// Builds an order from the user's server-side cart and the prices stored in
// the database. Nothing price-related is taken from the client request, so a
// user cannot change what they pay by editing the request in the browser.

export const DELIVERY_CHARGE = 50;
export const MAX_QTY_PER_ITEM = 50;

export class PricingError extends Error {
  constructor(message, status = 400) {
    super(message);
    this.status = status;
  }
}

/**
 * @param {Object} cartData  { [foodId]: quantity } as stored on the user
 * @param {Array}  foods     food documents from the database
 * @returns {{ items: Array, subtotal: number, amount: number }}
 */
export const buildOrderFromCart = (
  cartData,
  foods,
  deliveryCharge = DELIVERY_CHARGE,
) => {
  const entries = Object.entries(cartData || {}).filter(([, qty]) => qty > 0);
  if (entries.length === 0) {
    throw new PricingError("Your cart is empty");
  }

  const foodById = new Map(foods.map((food) => [String(food._id), food]));

  const items = entries.map(([foodId, qty]) => {
    if (!Number.isInteger(qty) || qty > MAX_QTY_PER_ITEM) {
      throw new PricingError("Invalid quantity in cart");
    }
    const food = foodById.get(String(foodId));
    if (!food) {
      throw new PricingError(
        "An item in your cart is no longer available",
        409,
      );
    }
    return {
      _id: String(food._id),
      name: food.name,
      price: food.price,
      quantity: qty,
    };
  });

  const subtotal = items.reduce(
    (sum, item) => sum + item.price * item.quantity,
    0,
  );
  return { items, subtotal, amount: subtotal + deliveryCharge };
};

// Stripe works in the smallest currency unit (paise). Rounding avoids
// floating point results like 199.99 * 100 = 19998.999...
export const toPaise = (rupees) => Math.round(rupees * 100);

// Total in paise computed the same way Stripe adds up the line items
// (each price rounded first), so the two can be compared exactly.
export const orderTotalPaise = (items, deliveryCharge = DELIVERY_CHARGE) =>
  items.reduce((sum, item) => sum + toPaise(item.price) * item.quantity, 0) +
  toPaise(deliveryCharge);
