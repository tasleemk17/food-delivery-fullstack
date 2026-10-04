// Order and payment tests. The database models and Stripe are replaced with
// mocks, so these tests run without MongoDB or a Stripe account.
import { jest } from "@jest/globals";
import request from "supertest";
import { tokenFor, USER_ID, ORDER_ID, FOOD_A, FOOD_B } from "./helpers.js";

const orderModel = {
  create: jest.fn(),
  findOne: jest.fn(),
  findById: jest.fn(),
  findByIdAndUpdate: jest.fn(),
  findOneAndUpdate: jest.fn(),
  find: jest.fn(),
};
const userModel = { findById: jest.fn(), findByIdAndUpdate: jest.fn() };
const foodModel = { find: jest.fn() };
const stripe = {
  checkout: { sessions: { create: jest.fn(), retrieve: jest.fn() } },
  webhooks: { constructEvent: jest.fn() },
};

jest.unstable_mockModule("../models/orderModel.js", () => ({
  default: orderModel,
  ORDER_STATUSES: [],
  ADMIN_SETTABLE_STATUSES: ["Food Processing", "Out for delivery", "Delivered"],
}));
jest.unstable_mockModule("../models/userModel.js", () => ({
  default: userModel,
}));
jest.unstable_mockModule("../models/foodModel.js", () => ({
  default: foodModel,
}));
jest.unstable_mockModule("../config/stripe.js", () => ({ default: stripe }));

const { default: app } = await import("../app.js");

const token = tokenFor(USER_ID);
const address = {
  firstName: "Asha",
  lastName: "K",
  email: "a@b.com",
  street: "1 MG Road",
  city: "Pune",
  state: "MH",
  zipcode: "411001",
  country: "India",
  phone: "9999999999",
};
const dbFoods = [
  { _id: FOOD_A, name: "Greek Salad", price: 120 },
  { _id: FOOD_B, name: "Veg Roll", price: 90 },
];
// 2 x 120 + 1 x 90 + 50 delivery
const EXPECTED_AMOUNT = 380;

beforeEach(() => {
  jest.clearAllMocks();
  userModel.findById.mockResolvedValue({
    _id: USER_ID,
    cartData: { [FOOD_A]: 2, [FOOD_B]: 1 },
  });
  foodModel.find.mockResolvedValue(dbFoods);
  orderModel.create.mockImplementation(async (doc) => ({
    _id: ORDER_ID,
    ...doc,
  }));
});

describe("price tampering", () => {
  test("COD order uses database prices, not the amount the browser sends", async () => {
    const tampered = {
      address,
      amount: 1,
      items: [{ _id: FOOD_A, name: "Greek Salad", price: 0.01, quantity: 2 }],
    };
    const res = await request(app)
      .post("/api/order/placecod")
      .set("token", token)
      .send(tampered);

    expect(res.status).toBe(201);
    const saved = orderModel.create.mock.calls[0][0];
    expect(saved.amount).toBe(EXPECTED_AMOUNT);
    expect(saved.items.find((i) => i._id === FOOD_A).price).toBe(120);
  });

  test("Stripe checkout is created with database prices", async () => {
    stripe.checkout.sessions.create.mockResolvedValue({
      id: "cs_1",
      url: "https://stripe.test/cs_1",
    });
    const res = await request(app)
      .post("/api/order/place")
      .set("token", token)
      .send({ address, amount: 1 });

    expect(res.status).toBe(200);
    const { line_items, metadata } =
      stripe.checkout.sessions.create.mock.calls[0][0];
    expect(line_items[0].price_data.unit_amount).toBe(12000);
    expect(metadata.orderId).toBe(ORDER_ID);
    // Cart is NOT cleared before payment is confirmed
    expect(userModel.findByIdAndUpdate).not.toHaveBeenCalled();
  });

  test("empty cart -> 400", async () => {
    userModel.findById.mockResolvedValue({ _id: USER_ID, cartData: {} });
    const res = await request(app)
      .post("/api/order/placecod")
      .set("token", token)
      .send({ address });
    expect(res.status).toBe(400);
    expect(orderModel.create).not.toHaveBeenCalled();
  });

  test("missing address field -> 400", async () => {
    const res = await request(app)
      .post("/api/order/placecod")
      .set("token", token)
      .send({ address: { ...address, city: "" } });
    expect(res.status).toBe(400);
  });
});

describe("payment verification", () => {
  const pendingOrder = {
    _id: ORDER_ID,
    userId: USER_ID,
    payment: false,
    amount: EXPECTED_AMOUNT,
    stripeSessionId: "cs_1",
    items: [
      { _id: FOOD_A, price: 120, quantity: 2 },
      { _id: FOOD_B, price: 90, quantity: 1 },
    ],
  };
  const paidSession = {
    payment_status: "paid",
    metadata: { orderId: ORDER_ID },
    amount_total: 38000,
    currency: "inr",
  };

  test("the old bypass fails: success=true from the browser does not mark an unpaid order as paid", async () => {
    orderModel.findOne.mockResolvedValue(pendingOrder);
    stripe.checkout.sessions.retrieve.mockResolvedValue({
      ...paidSession,
      payment_status: "unpaid",
    });

    const res = await request(app)
      .post("/api/order/verify")
      .set("token", token)
      .send({ orderId: ORDER_ID, success: "true" });

    expect(res.body.success).toBe(false);
    expect(orderModel.findOneAndUpdate).not.toHaveBeenCalled();
  });

  test("a genuinely paid Stripe session marks the order paid and clears the cart", async () => {
    orderModel.findOne.mockResolvedValue(pendingOrder);
    stripe.checkout.sessions.retrieve.mockResolvedValue(paidSession);
    orderModel.findOneAndUpdate.mockResolvedValue({
      ...pendingOrder,
      payment: true,
    });

    const res = await request(app)
      .post("/api/order/verify")
      .set("token", token)
      .send({ orderId: ORDER_ID });

    expect(res.body.success).toBe(true);
    expect(orderModel.findOneAndUpdate).toHaveBeenCalledWith(
      { _id: ORDER_ID, payment: false },
      { payment: true, status: "Food Processing" },
      { new: true },
    );
    expect(userModel.findByIdAndUpdate).toHaveBeenCalledWith(USER_ID, {
      cartData: {},
    });
  });

  test("a paid session for a different amount is rejected", async () => {
    orderModel.findOne.mockResolvedValue(pendingOrder);
    stripe.checkout.sessions.retrieve.mockResolvedValue({
      ...paidSession,
      amount_total: 100,
    });

    const res = await request(app)
      .post("/api/order/verify")
      .set("token", token)
      .send({ orderId: ORDER_ID });

    expect(res.body.success).toBe(false);
    expect(orderModel.findOneAndUpdate).not.toHaveBeenCalled();
  });

  test("a user cannot verify someone else's order", async () => {
    // findOne is scoped to { _id, userId }, so another user's order is not found
    orderModel.findOne.mockResolvedValue(null);
    const res = await request(app)
      .post("/api/order/verify")
      .set("token", token)
      .send({ orderId: ORDER_ID });

    expect(res.status).toBe(404);
    expect(orderModel.findOne).toHaveBeenCalledWith({
      _id: ORDER_ID,
      userId: USER_ID,
    });
  });
});

describe("Stripe webhook", () => {
  beforeAll(() => {
    process.env.STRIPE_WEBHOOK_SECRET = "whsec_test";
  });

  test("a request without a valid Stripe signature -> 400", async () => {
    stripe.webhooks.constructEvent.mockImplementation(() => {
      throw new Error("bad signature");
    });
    const res = await request(app)
      .post("/api/order/webhook")
      .set("Content-Type", "application/json")
      .send(JSON.stringify({ type: "checkout.session.completed" }));

    expect(res.status).toBe(400);
    expect(orderModel.findOneAndUpdate).not.toHaveBeenCalled();
  });

  test("a signed checkout.session.completed event marks the order paid", async () => {
    stripe.webhooks.constructEvent.mockReturnValue({
      type: "checkout.session.completed",
      data: {
        object: {
          payment_status: "paid",
          metadata: { orderId: ORDER_ID },
          amount_total: 38000,
          currency: "inr",
        },
      },
    });
    orderModel.findById.mockResolvedValue({
      _id: ORDER_ID,
      userId: USER_ID,
      items: [
        { price: 120, quantity: 2 },
        { price: 90, quantity: 1 },
      ],
    });
    orderModel.findOneAndUpdate.mockResolvedValue({
      _id: ORDER_ID,
      userId: USER_ID,
    });

    const res = await request(app)
      .post("/api/order/webhook")
      .set("Content-Type", "application/json")
      .set("stripe-signature", "t=1,v1=abc")
      .send("{}");

    expect(res.status).toBe(200);
    expect(orderModel.findOneAndUpdate).toHaveBeenCalled();
  });
});

describe("admin status update", () => {
  const adminToken = tokenFor(USER_ID, "admin");

  test("rejects statuses outside the allowed list", async () => {
    const res = await request(app)
      .post("/api/order/status")
      .set("token", adminToken)
      .send({ orderId: ORDER_ID, status: "Refunded to attacker" });
    expect(res.status).toBe(400);
  });

  test("cannot move an unpaid Stripe order into delivery", async () => {
    orderModel.findOneAndUpdate.mockResolvedValue(null);
    const res = await request(app)
      .post("/api/order/status")
      .set("token", adminToken)
      .send({ orderId: ORDER_ID, status: "Out for delivery" });
    expect(res.status).toBe(409);
  });
});
