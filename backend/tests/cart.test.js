// A food deleted from the menu must not get stuck in carts and block checkout.
// Models are mocked, so no MongoDB is needed.
import { jest } from "@jest/globals";
import fs from "fs";
import request from "supertest";
import { tokenFor, USER_ID, FOOD_A } from "./helpers.js";

const GHOST_FOOD = "64b0000000000000000000ff"; // was in the cart, then deleted from the menu

const userModel = {
  findById: jest.fn(),
  findByIdAndUpdate: jest.fn(),
  updateMany: jest.fn(),
};
const foodModel = {
  find: jest.fn(),
  findByIdAndDelete: jest.fn(),
  create: jest.fn(),
  exists: jest.fn(),
};

jest.unstable_mockModule("../models/userModel.js", () => ({
  default: userModel,
}));
jest.unstable_mockModule("../models/foodModel.js", () => ({
  default: foodModel,
}));

const { default: app } = await import("../app.js");

const userToken = tokenFor(USER_ID);
const adminToken = tokenFor(USER_ID, "admin");
beforeEach(() => {
  jest.clearAllMocks();
  userModel.findById.mockResolvedValue({
    _id: USER_ID,
    cartData: { [FOOD_A]: 1, [GHOST_FOOD]: 2 },
  });
  // Only FOOD_A is still on the menu
  foodModel.find.mockResolvedValue([
    { _id: FOOD_A, name: "Cupcake", price: 200 },
  ]);
});

describe("loading the cart", () => {
  test("drops deleted foods and saves the cleaned cart", async () => {
    const res = await request(app)
      .post("/api/cart/get")
      .set("token", userToken)
      .send({});

    expect(res.status).toBe(200);
    expect(res.body.cartData).toEqual({ [FOOD_A]: 1 });
    expect(userModel.findByIdAndUpdate).toHaveBeenCalledWith(USER_ID, {
      cartData: { [FOOD_A]: 1 },
    });
  });

  test("does not write to the database when nothing changed", async () => {
    userModel.findById.mockResolvedValue({
      _id: USER_ID,
      cartData: { [FOOD_A]: 1 },
    });

    const res = await request(app)
      .post("/api/cart/get")
      .set("token", userToken)
      .send({});

    expect(res.body.cartData).toEqual({ [FOOD_A]: 1 });
    expect(userModel.findByIdAndUpdate).not.toHaveBeenCalled();
  });
});

describe("admin deletes a food", () => {
  test("it is removed from every saved cart", async () => {
    foodModel.findByIdAndDelete.mockResolvedValue({
      _id: GHOST_FOOD,
      image: "x.png",
    });

    const res = await request(app)
      .post("/api/food/remove")
      .set("token", adminToken)
      .send({ id: GHOST_FOOD });

    expect(res.status).toBe(200);
    expect(userModel.updateMany).toHaveBeenCalledWith(
      { [`cartData.${GHOST_FOOD}`]: { $exists: true } },
      { $unset: { [`cartData.${GHOST_FOOD}`]: "" } },
    );
  });
});

describe("admin adds a food", () => {
  test("spaces around the name are trimmed", async () => {
    foodModel.create.mockResolvedValue({});

    const res = await request(app)
      .post("/api/food/add")
      .set("token", adminToken)
      .field("name", "  Cupcake ")
      .field("description", "Sweet")
      .field("price", "200")
      .field("category", "Cake")
      .attach("image", Buffer.from("fake-png"), {
        filename: "test.png",
        contentType: "image/png",
      });

    expect(res.status).toBe(201);
    const saved = foodModel.create.mock.calls[0][0];
    expect(saved.name).toBe("Cupcake");

    // Remove the test image multer wrote to uploads/
    fs.unlinkSync(`uploads/${saved.image}`);
  });
});
