// Takes the portfolio pictures of the running stack (the same flow a customer follows).
// Run:  cd e2e && node demo/portfolio-shots.mjs
// Pictures go to ../docs/portfolio. A brand new fake customer (@tomato.test) is used, so no real
// personal data can appear on any picture.
import { chromium } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";
import { URLS } from "../playwright.config.js";
import { newCustomer, signUp, address } from "../tests/helpers.js";

const OUT = path.resolve("../docs/portfolio");
fs.mkdirSync(OUT, { recursive: true });

const channel = process.env.E2E_BROWSER_CHANNEL || (process.platform === "win32" ? "msedge" : undefined);
const browser = await chromium.launch({ channel });
const context = await browser.newContext({
  baseURL: URLS.app,
  viewport: { width: 1440, height: 900 },
  deviceScaleFactor: 1.5,
});
const page = await context.newPage();
const customer = newCustomer();

const settle = async () => {
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(700); // let fade-in animations finish
};
const shot = async (name, options = {}) => {
  await settle();
  await page.screenshot({ path: path.join(OUT, name), ...options });
  console.log("saved", name);
};
const step = async (name, fn) => {
  try {
    await fn();
  } catch (e) {
    console.log(`SKIPPED ${name}: ${e.message.split("\n")[0]}`);
  }
};

await step("home", async () => {
  await page.goto("/");
  await page.locator(".food-item").first().waitFor();
  await shot("01-home.png");
});

await step("menu", async () => {
  await page.locator("#explore-menu").scrollIntoViewIfNeeded().catch(() => {});
  await page.locator(".food-display").scrollIntoViewIfNeeded();
  await shot("02-menu.png");
});

await step("sign up", async () => {
  await signUp(page, customer);
  // After a login the website loads the saved cart; wait for it so it cannot wipe the dishes added next
  await settle();
  await page.waitForTimeout(1500);
});

await step("cart", async () => {
  const adds = page.locator(".food-item .add");
  await adds.nth(0).click();
  await adds.nth(1).click();
  await page.locator('a[href="/cart"]').click();
  await page.locator(".cart-items-item").first().waitFor();
  await page.getByRole("heading", { name: "Customers also ordered" }).waitFor({ timeout: 15000 });
  await shot("03-cart-customers-also-ordered.png", { fullPage: true });
});

await step("checkout", async () => {
  await page.getByRole("button", { name: "PROCEED TO CHECKOUT" }).click();
  for (const [name, value] of Object.entries(address(customer.email))) {
    await page.locator(`.place-order input[name="${name}"]`).fill(value);
  }
  await page.getByText("COD ( Cash on delivery )").click();
  await shot("04-checkout.png", { fullPage: true });
  await page.getByRole("button", { name: "Place Order" }).click();
  await page.waitForURL(/\/myorders$/);
  await page.locator(".my-orders-order").first().waitFor();
  await shot("05-my-orders.png");
});

await step("chatbot", async () => {
  await page.goto("/");
  await page.getByRole("button", { name: "Open chat" }).click();
  await page.getByPlaceholder("Ask about the menu...").fill("Which salads do you have, and what is the delivery fee?");
  await page.getByRole("button", { name: "Send" }).click();
  await page.locator(".chat-message.assistant").nth(1).waitFor({ timeout: 40000 });
  // Long answers scroll the panel to its end: bring the question back to the top so it is on the picture
  await page.locator(".chat-message.user").first().evaluate((el) => el.scrollIntoView({ block: "start" }));
  await shot("06-chatbot.png");
});

await step("email", async () => {
  const mail = await context.newPage();
  await mail.setViewportSize({ width: 1440, height: 900 });
  await mail.goto(URLS.mail);
  await mail.getByText(customer.email).first().waitFor({ timeout: 30000 });
  await mail.getByText("Order confirmed").first().click();
  await mail.waitForTimeout(1500);
  // Only the email itself: the left column of MailHog has a joke box that does not belong on a portfolio
  await mail.screenshot({ path: path.join(OUT, "07-confirmation-email.png"), clip: { x: 245, y: 105, width: 1195, height: 450 } });
  console.log("saved 07-confirmation-email.png");
  await mail.close();
});

await step("mobile", async () => {
  const phone = await browser.newContext({
    baseURL: URLS.app,
    viewport: { width: 390, height: 844 },
    deviceScaleFactor: 2,
    isMobile: true,
  });
  const p = await phone.newPage();
  await p.goto("/");
  await p.locator(".food-item").first().waitFor();
  await p.waitForTimeout(1500);
  await p.screenshot({ path: path.join(OUT, "08-mobile.png") });
  console.log("saved 08-mobile.png");
  await phone.close();
});

await browser.close();
console.log("done ->", OUT);
