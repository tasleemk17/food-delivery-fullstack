import { test, expect } from "@playwright/test";

test("a wrong password shows an error message and keeps the visitor logged out", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "sign in" }).click();
  const popup = page.locator(".login-popup");
  await popup.getByText("Login here").click();
  await popup.getByPlaceholder("Your email").fill("nobody@tomato.test");
  await popup.getByPlaceholder("Password").fill("not-the-password");
  await popup.locator(".login-popup-condition input").check();
  await popup.getByRole("button", { name: "Login" }).click();

  await expect(page.locator(".Toastify__toast")).toContainText("Invalid email or password");
  await expect(popup).toBeVisible(); // still open, so the visitor can try again
  await expect(page.getByRole("button", { name: "sign in" })).toBeVisible();
});
