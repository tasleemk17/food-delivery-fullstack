import mongoose from "mongoose";
import orderModel, { ADMIN_SETTABLE_STATUSES } from "../models/orderModel.js";
import userModel from "../models/userModel.js";
import foodModel from "../models/foodModel.js";
import stripe from "../config/stripe.js";
import {
  buildOrderFromCart,
  PricingError,
  DELIVERY_CHARGE,
  toPaise,
  orderTotalPaise,
} from "../services/pricing.js";

const currency = "inr";
const frontendUrl = () => process.env.FRONTEND_URL || "http://localhost:5173";

const ADDRESS_FIELDS = [
  "firstName",
  "lastName",
  "email",
  "street",
  "city",
  "state",
  "zipcode",
  "country",
  "phone",
];

const validateAddress = (address) => {
  if (!address || typeof address !== "object") return null;
  const clean = {};
  for (const field of ADDRESS_FIELDS) {
    const value = address[field];
    if (typeof value !== "string" || !value.trim()) return null;
    clean[field] = value.trim().slice(0, 200);
  }
  return clean;
};

// Shared first step for both payment methods: price the order on the server.
const priceUserCart = async (userId) => {
  const user = await userModel.findById(userId);
  if (!user) throw new PricingError("User not found", 404);
  const foodIds = Object.keys(user.cartData || {}).filter((id) =>
    mongoose.isValidObjectId(id),
  );
  const foods = await foodModel.find({ _id: { $in: foodIds } });
  return buildOrderFromCart(user.cartData, foods);
};

const sendError = (res, error) => {
  if (error instanceof PricingError) {
    return res
      .status(error.status)
      .json({ success: false, message: error.message });
  }
  console.error(error);
  return res.status(500).json({ success: false, message: "Error" });
};

/**
 * Marks an order as paid exactly once. Both the Stripe webhook and the
 * /verify endpoint call this, and whichever arrives second changes nothing,
 * because the update only matches orders that are still unpaid.
 */
export const markOrderPaid = async (orderId) => {
  const order = await orderModel.findOneAndUpdate(
    { _id: orderId, payment: false },
    { payment: true, status: "Food Processing" },
    { new: true },
  );
  if (order) {
    // The cart is cleared only after payment succeeds, so a cancelled
    // payment does not wipe the user's cart.
    await userModel.findByIdAndUpdate(order.userId, { cartData: {} });
  }
  return order;
};

// A Stripe session counts as payment for an order only if every detail matches.
const sessionPaysForOrder = (session, order) =>
  session.payment_status === "paid" &&
  session.metadata?.orderId === String(order._id) &&
  session.amount_total === orderTotalPaise(order.items) &&
  session.currency === currency;

// Placing user order using Stripe
const placeOrder = async (req, res) => {
  const address = validateAddress(req.body.address);
  if (!address) {
    return res
      .status(400)
      .json({
        success: false,
        message: "Please fill in the full delivery address",
      });
  }
  try {
    // req.body.items and req.body.amount are deliberately ignored.
    const { items, amount } = await priceUserCart(req.userId);

    const order = await orderModel.create({
      userId: req.userId,
      items,
      amount,
      address,
      paymentMethod: "stripe",
      status: "Payment Pending",
    });

    const line_items = items.map((item) => ({
      price_data: {
        currency,
        product_data: { name: item.name },
        unit_amount: toPaise(item.price),
      },
      quantity: item.quantity,
    }));
    line_items.push({
      price_data: {
        currency,
        product_data: { name: "Delivery Charge" },
        unit_amount: toPaise(DELIVERY_CHARGE),
      },
      quantity: 1,
    });

    const session = await stripe.checkout.sessions.create({
      line_items,
      mode: "payment",
      metadata: { orderId: String(order._id) },
      success_url: `${frontendUrl()}/verify?orderId=${order._id}&session_id={CHECKOUT_SESSION_ID}`,
      cancel_url: `${frontendUrl()}/verify?orderId=${order._id}`,
    });

    await orderModel.findByIdAndUpdate(order._id, {
      stripeSessionId: session.id,
    });
    res.json({ success: true, session_url: session.url });
  } catch (error) {
    sendError(res, error);
  }
};

// Placing user order with cash on delivery
const placeOrderCod = async (req, res) => {
  const address = validateAddress(req.body.address);
  if (!address) {
    return res
      .status(400)
      .json({
        success: false,
        message: "Please fill in the full delivery address",
      });
  }
  try {
    const { items, amount } = await priceUserCart(req.userId);
    await orderModel.create({
      userId: req.userId,
      items,
      amount,
      address,
      paymentMethod: "cod",
      status: "Food Processing",
    });
    await userModel.findByIdAndUpdate(req.userId, { cartData: {} });
    res.status(201).json({ success: true, message: "Order Placed" });
  } catch (error) {
    sendError(res, error);
  }
};

/**
 * Called by the Verify page after Stripe redirects back.
 * Before: it trusted `success: "true"` sent by the browser, so anyone could
 * mark any order as paid. Now it asks Stripe directly and only for the
 * logged-in user's own order.
 */
const verifyOrder = async (req, res) => {
  const { orderId } = req.body;
  if (!mongoose.isValidObjectId(orderId)) {
    return res.status(400).json({ success: false, message: "Invalid order" });
  }
  try {
    const order = await orderModel.findOne({
      _id: orderId,
      userId: req.userId,
    });
    if (!order) {
      return res
        .status(404)
        .json({ success: false, message: "Order not found" });
    }
    if (order.payment) {
      return res.json({ success: true, message: "Paid" });
    }
    if (!order.stripeSessionId) {
      return res.json({ success: false, message: "Not Paid" });
    }

    const session = await stripe.checkout.sessions.retrieve(
      order.stripeSessionId,
    );
    if (sessionPaysForOrder(session, order)) {
      await markOrderPaid(order._id);
      return res.json({ success: true, message: "Paid" });
    }
    res.json({ success: false, message: "Not Paid" });
  } catch (error) {
    sendError(res, error);
  }
};

/**
 * Stripe calls this endpoint directly (server to server), so payment is
 * recorded even if the user closes the browser before the redirect.
 * The signature check proves the request really came from Stripe.
 */
const stripeWebhook = async (req, res) => {
  const secret = process.env.STRIPE_WEBHOOK_SECRET;
  if (!secret) {
    return res
      .status(503)
      .json({ success: false, message: "Webhook not configured" });
  }

  let event;
  try {
    event = stripe.webhooks.constructEvent(
      req.body,
      req.headers["stripe-signature"],
      secret,
    );
  } catch {
    return res
      .status(400)
      .json({ success: false, message: "Invalid signature" });
  }

  try {
    const session = event.data.object;
    const orderId = session.metadata?.orderId;

    if (
      event.type === "checkout.session.completed" &&
      mongoose.isValidObjectId(orderId)
    ) {
      const order = await orderModel.findById(orderId);
      if (order && sessionPaysForOrder(session, order)) {
        await markOrderPaid(order._id);
      }
    }
    if (
      event.type === "checkout.session.expired" &&
      mongoose.isValidObjectId(orderId)
    ) {
      await orderModel.findOneAndUpdate(
        { _id: orderId, payment: false },
        { status: "Payment Failed" },
      );
    }
    res.json({ received: true });
  } catch (error) {
    console.error(error);
    // A non-2xx answer makes Stripe retry the event later.
    res.status(500).json({ received: false });
  }
};

// Listing all orders (admin)
const listOrders = async (req, res) => {
  try {
    const orders = await orderModel.find({}).sort({ date: 1 });
    res.json({ success: true, data: orders });
  } catch (error) {
    sendError(res, error);
  }
};

// Orders of the logged-in user
const userOrders = async (req, res) => {
  try {
    const orders = await orderModel
      .find({ userId: req.userId })
      .sort({ date: -1 });
    res.json({ success: true, data: orders });
  } catch (error) {
    sendError(res, error);
  }
};

// Update delivery status (admin)
const updateStatus = async (req, res) => {
  const { orderId, status } = req.body;
  if (!ADMIN_SETTABLE_STATUSES.includes(status)) {
    return res.status(400).json({ success: false, message: "Invalid status" });
  }
  if (!mongoose.isValidObjectId(orderId)) {
    return res.status(400).json({ success: false, message: "Invalid order" });
  }
  try {
    // An unpaid Stripe order cannot be pushed into delivery.
    const order = await orderModel.findOneAndUpdate(
      {
        _id: orderId,
        $or: [{ paymentMethod: { $ne: "stripe" } }, { payment: true }],
      },
      { status },
      { new: true },
    );
    if (!order) {
      return res
        .status(409)
        .json({ success: false, message: "Order not found or not paid yet" });
    }
    res.json({ success: true, message: "Status Updated" });
  } catch (error) {
    sendError(res, error);
  }
};

export {
  placeOrder,
  listOrders,
  userOrders,
  updateStatus,
  verifyOrder,
  placeOrderCod,
  stripeWebhook,
};
