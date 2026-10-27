import { test, expect } from "@playwright/test";
import { newCustomer, signUp, logIn, waitForMail, address } from "./helpers.js";

const customer = newCustomer();

// The tests run in order and build on each other, like a real customer's visit.
test.describe.serial("customer journey", () => {
  test("the menu loads from the backend", async ({ page }) => {
    await page.goto("/");
    await expect(page.locator(".food-item").first()).toBeVisible();
    expect(await page.locator(".food-item").count()).toBeGreaterThan(3);
    await expect(page.locator(".food-item-price").first()).toContainText("₹");
  });

  test("a visitor who is not logged in is asked to sign in before chatting", async ({ page }) => {
    await page.goto("/");
    await page.getByRole("button", { name: "Open chat" }).click();
    await expect(page.getByText("Log in to chat with the assistant.")).toBeVisible();
    await expect(page.getByPlaceholder("Ask about the menu...")).toHaveCount(0);
    await page.getByRole("button", { name: "Sign in", exact: true }).click();
    await expect(page.locator(".login-popup")).toBeVisible();
  });

  test("a customer signs up, orders cash on delivery and sees the order", async ({ page, request }) => {
    await signUp(page, customer);
    await expect(page.locator(".navbar-profile")).toBeVisible();

    // Add the first dish that can be ordered (a sold out dish has no add button)
    await page.locator(".food-item .add").first().click();
    await expect(page.locator(".food-item-counter").first()).toContainText("1");

    await page.locator('a[href="/cart"]').click();
    await expect(page).toHaveURL(/\/cart$/);
    await expect(page.locator(".cart-items-item")).toHaveCount(1);

    // order-service charges the delivery fee on top of the price
    const subtotal = await page.locator(".cart-total-details").first().innerText();
    expect(subtotal).toContain("₹");

    await page.getByRole("button", { name: "PROCEED TO CHECKOUT" }).click();
    await expect(page).toHaveURL(/\/order$/);

    const a = address(customer.email);
    for (const [name, value] of Object.entries(a)) {
      await page.locator(`.place-order input[name="${name}"]`).fill(value);
    }
    await page.getByText("COD ( Cash on delivery )").click();
    await page.getByRole("button", { name: "Place Order" }).click();

    await expect(page).toHaveURL(/\/myorders$/);
    const order = page.locator(".my-orders-order").first();
    await expect(order).toBeVisible();
    await expect(order).toContainText("Food Processing");

    // The order travelled order-service -> Kafka -> notification-service -> MailHog
    const mail = await waitForMail(request, customer.email, "Order confirmed");
    expect(mail, "the confirmation email arrived in MailHog").not.toBeNull();
  });

  test("the cart suggests dishes that other customers ordered together", async ({ page }) => {
    await logIn(page, customer);
    await page.locator(".food-item .add").first().click();
    await page.locator('a[href="/cart"]').click();
    // The shop already has order history, so the section is filled (popular dishes at least)
    await expect(page.getByRole("heading", { name: "Customers also ordered" })).toBeVisible();
    const suggestions = page.locator(".also-ordered-item");
    expect(await suggestions.count()).toBeGreaterThan(0);

    // Adding a suggestion puts it in the cart
    const before = await page.locator(".cart-items-item").count();
    await suggestions.first().getByRole("button", { name: "Add" }).click();
    await expect(page.locator(".cart-items-item")).toHaveCount(before + 1);
  });

  test("a logged in customer gets an answer from the assistant", async ({ page }) => {
    await logIn(page, customer);

    await page.getByRole("button", { name: "Open chat" }).click();
    await page.getByPlaceholder("Ask about the menu...").fill("How can I pay for my order?");
    await page.getByRole("button", { name: "Send" }).click();

    const reply = page.locator(".chat-message.assistant").nth(1); // the first one is the greeting
    await expect(reply).toBeVisible({ timeout: 40_000 });
    await expect(reply).not.toHaveClass(/error/);
    // Answered from the help articles, with or without Claude
    await expect(reply).toContainText(/cash|card|stripe/i);
    await expect(reply.locator(".chat-sources").first()).toBeVisible();
  });
});
