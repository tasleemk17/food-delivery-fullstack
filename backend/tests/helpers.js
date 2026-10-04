import jwt from "jsonwebtoken";

export const JWT_SECRET = "test-secret";
process.env.JWT_SECRET = JWT_SECRET;

export const tokenFor = (id, role = "user") =>
  jwt.sign({ id, role }, JWT_SECRET);

// Valid MongoDB ObjectId strings for tests
export const USER_ID = "64b000000000000000000001";
export const OTHER_USER_ID = "64b000000000000000000002";
export const ORDER_ID = "64b0000000000000000000a1";
export const FOOD_A = "64b0000000000000000000f1";
export const FOOD_B = "64b0000000000000000000f2";
