import { test, expect } from "@playwright/test";
import { URLS } from "../playwright.config.js";

// Checks the front door of the stack: what the gateway lets through without a login, and what it never does.
const api = (path) => `${URLS.api}${path}`;

test("public routes work without a login", async ({ request }) => {
  expect((await request.get(api("/api/food/list"))).status()).toBe(200);
  expect((await request.get(api("/api/inventory/availability"))).status()).toBe(200);
  expect((await request.get(api("/api/ai/recommendations/popular"))).status()).toBe(200);
  expect((await request.get(api("/actuator/health"))).status()).toBe(200);
});

test("private routes need a login", async ({ request }) => {
  expect((await request.post(api("/api/cart/get"), { data: {} })).status()).toBe(401);
  expect((await request.post(api("/api/order/placecod"), { data: {} })).status()).toBe(401);
  expect((await request.post(api("/api/ai/chat"), { data: { message: "hi" } })).status()).toBe(401);
});

test("a forged token is rejected", async ({ request }) => {
  const forged = "eyJhbGciOiJIUzI1NiJ9.eyJpZCI6IjEiLCJyb2xlIjoiYWRtaW4ifQ.invalidsignature";
  const response = await request.post(api("/api/cart/get"), { data: {}, headers: { token: forged } });
  expect(response.status()).toBe(401);
});

test("admin and stock routes are closed to a normal customer", async ({ request }) => {
  const stock = await request.put(api("/api/inventory/64b000000000000000000001"), { data: { quantity: 1 } });
  expect([401, 403]).toContain(stock.status());
  const list = await request.post(api("/api/order/list"), { data: {} });
  expect([401, 403]).toContain(list.status());
});

test("service to service routes cannot be reached from outside", async ({ request }) => {
  const response = await request.get(api("/internal/anything"));
  expect([403, 404]).toContain(response.status());
});
