import mongoose from "mongoose";

export const ORDER_STATUSES = [
  "Payment Pending",
  "Payment Failed",
  "Food Processing",
  "Out for delivery",
  "Delivered",
];

// Statuses an admin may set by hand. Payment statuses are set only by the
// server after checking with Stripe.
export const ADMIN_SETTABLE_STATUSES = [
  "Food Processing",
  "Out for delivery",
  "Delivered",
];

const orderSchema = new mongoose.Schema({
  userId: { type: String, required: true },
  items: { type: Array, required: true },
  amount: { type: Number, required: true },
  address: { type: Object, required: true },
  status: { type: String, enum: ORDER_STATUSES, default: "Food Processing" },
  paymentMethod: { type: String, enum: ["cod", "stripe"], default: "cod" },
  stripeSessionId: { type: String },
  // Bug fix: was `default: Date.now()`, which runs once when the server starts,
  // so every order got the same date. Passing the function runs it per order.
  date: { type: Date, default: Date.now },
  payment: { type: Boolean, default: false },
});

const orderModel =
  mongoose.models.order || mongoose.model("order", orderSchema);
export default orderModel;
