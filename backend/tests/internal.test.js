// Tests for the /internal routes used by order-service.
// Database models are mocked, so no MongoDB is needed.
import { jest } from "@jest/globals";
import request from "supertest";
import { USER_ID, FOOD_A, FOOD_B } from "./helpers.js";

const userModel = { findById: jest.fn(), findByIdAndUpdate: jest.fn() };
const foodModel = { find: jest.fn() };

jest.unstable_mockModule("../models/userModel.js", () => ({
  default: userModel,
}));
jest.unstable_mockModule("../models/foodModel.js", () => ({
  default: foodModel,
}));

const { default: app } = await import("../app.js");

const KEY = "internal-test-key-0123456789";

beforeEach(() => {
  jest.clearAllMocks();
  process.env.INTERNAL_API_KEY = KEY;
  userModel.findById.mockResolvedValue({
    _id: USER_ID,
    cartData: { [FOOD_A]: 2, [FOOD_B]: 0 },
  });
  foodModel.find.mockResolvedValue([
    { _id: FOOD_A, name: "Greek Salad", price: 120 },
  ]);
});

describe("internal key", () => {
  test("no key -> 401", async () => {
    const res = await request(app).get(`/internal/users/${USER_ID}/cart`);
    expect(res.status).toBe(401);
    expect(userModel.findById).not.toHaveBeenCalled();
  });

  test("wrong key -> 401", async () => {
    const res = await request(app)
      .get(`/internal/users/${USER_ID}/cart`)
      .set("X-Internal-Key", "wrong-key");
    expect(res.status).toBe(401);
  });

  test("a user's login token is not enough", async () => {
    const res = await request(app)
      .get(`/internal/users/${USER_ID}/cart`)
      .set("token", "any-user-token");
    expect(res.status).toBe(401);
  });

  test("routes stay switched off when INTERNAL_API_KEY is not set", async () => {
    delete process.env.INTERNAL_API_KEY;
    const res = await request(app)
      .get(`/internal/users/${USER_ID}/cart`)
      .set("X-Internal-Key", KEY);
    expect(res.status).toBe(503);
  });
});

describe("GET /internal/users/:userId/cart", () => {
  test("returns the cart (without zero quantities) and database prices", async () => {
    const res = await request(app)
      .get(`/internal/users/${USER_ID}/cart`)
      .set("X-Internal-Key", KEY);

    expect(res.status).toBe(200);
    expect(res.body.cart).toEqual({ [FOOD_A]: 2 });
    expect(res.body.foods).toEqual([
      { id: FOOD_A, name: "Greek Salad", price: 120 },
    ]);
  });

  test("invalid user id -> 400", async () => {
    const res = await request(app)
      .get("/internal/users/not-an-id/cart")
      .set("X-Internal-Key", KEY);
    expect(res.status).toBe(400);
  });

  test("unknown user -> 404", async () => {
    userModel.findById.mockResolvedValue(null);
    const res = await request(app)
      .get(`/internal/users/${USER_ID}/cart`)
      .set("X-Internal-Key", KEY);
    expect(res.status).toBe(404);
  });
});

describe("POST /internal/users/:userId/cart/clear", () => {
  test("empties the cart", async () => {
    const res = await request(app)
      .post(`/internal/users/${USER_ID}/cart/clear`)
      .set("X-Internal-Key", KEY);

    expect(res.status).toBe(200);
    expect(userModel.findByIdAndUpdate).toHaveBeenCalledWith(USER_ID, {
      cartData: {},
    });
  });

  test("needs the key too", async () => {
    const res = await request(app).post(
      `/internal/users/${USER_ID}/cart/clear`,
    );
    expect(res.status).toBe(401);
    expect(userModel.findByIdAndUpdate).not.toHaveBeenCalled();
  });
});
