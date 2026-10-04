// Access-control tests. Every request here is rejected by middleware before
// any database call, so no database is needed.
import { tokenFor, USER_ID } from "./helpers.js";
import request from "supertest";

const { default: app } = await import("../app.js");

const userToken = tokenFor(USER_ID, "user");

describe("customer routes need a valid login", () => {
  test.each([
    ["post", "/api/order/place"],
    ["post", "/api/order/placecod"],
    ["post", "/api/order/userorders"],
    ["post", "/api/order/verify"],
    ["post", "/api/cart/get"],
  ])("%s %s without token -> 401", async (method, path) => {
    const res = await request(app)[method](path).send({});
    expect(res.status).toBe(401);
    expect(res.body.success).toBe(false);
  });

  test("a forged token -> 401", async () => {
    const res = await request(app)
      .post("/api/order/userorders")
      .set("token", "not-a-real-jwt");
    expect(res.status).toBe(401);
  });

  test("a token signed with another secret -> 401", async () => {
    const jwt = (await import("jsonwebtoken")).default;
    const forged = jwt.sign({ id: USER_ID, role: "admin" }, "attacker-secret");
    const res = await request(app).get("/api/order/list").set("token", forged);
    expect(res.status).toBe(401);
  });
});

describe("admin routes reject normal users", () => {
  test.each([
    ["get", "/api/order/list"],
    ["post", "/api/order/status"],
    ["post", "/api/food/remove"],
    ["post", "/api/food/add"],
  ])("%s %s with a user token -> 403", async (method, path) => {
    const res = await request(app)
      [method](path)
      .set("token", userToken)
      .send({});
    expect(res.status).toBe(403);
  });

  test.each([
    ["get", "/api/order/list"],
    ["post", "/api/order/status"],
    ["post", "/api/food/remove"],
  ])("%s %s without token -> 401", async (method, path) => {
    const res = await request(app)[method](path).send({});
    expect(res.status).toBe(401);
  });
});

test("Authorization: Bearer header is also accepted", async () => {
  const res = await request(app)
    .get("/api/order/list")
    .set("Authorization", `Bearer ${userToken}`);
  // Authenticated but not admin -> 403 (not 401)
  expect(res.status).toBe(403);
});
