import { URLS } from "../playwright.config.js";

// One fresh customer per test run. The address ends in .test, a reserved name that can never be a real mailbox.
// The password is random and exists only for this run.
export function newCustomer() {
  const stamp = `${Date.now()}${Math.floor(Math.random() * 1000)}`;
  return {
    name: "E2E Customer",
    email: `e2e.${stamp}@tomato.test`,
    password: `Pw-${Math.random().toString(36).slice(2)}-${stamp}`,
  };
}

export const address = (email) => ({
  firstName: "E2E",
  lastName: "Customer",
  email,
  street: "1 Test Street",
  city: "Testville",
  state: "Test State",
  zipcode: "000000",
  country: "India",
  phone: "0000000000",
});

// Signs up through the website's own popup. The browser then holds the login token.
export async function signUp(page, customer) {
  await page.goto("/");
  await page.getByRole("button", { name: "sign in" }).click();
  const popup = page.locator(".login-popup");
  await popup.getByPlaceholder("Your name").fill(customer.name);
  await popup.getByPlaceholder("Your email").fill(customer.email);
  await popup.getByPlaceholder("Password").fill(customer.password);
  await popup.locator(".login-popup-condition input").check();
  await popup.getByRole("button", { name: "Create account" }).click();
  await page.locator(".login-popup").waitFor({ state: "detached" });
}

// Logs in again with the account made by signUp (every test starts with an empty browser).
export async function logIn(page, customer) {
  await page.goto("/");
  await page.getByRole("button", { name: "sign in" }).click();
  const popup = page.locator(".login-popup");
  await popup.getByText("Login here").click();
  await popup.getByPlaceholder("Your email").fill(customer.email);
  await popup.getByPlaceholder("Password").fill(customer.password);
  await popup.locator(".login-popup-condition input").check();
  await popup.getByRole("button", { name: "Login" }).click();
  await popup.waitFor({ state: "detached" });
}

// Polls MailHog (the test inbox) until a mail for this address with a subject like this one arrives.
export async function waitForMail(request, to, subjectPart, timeoutMs = 30_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const response = await request.get(`${URLS.mail}/api/v2/search`, { params: { kind: "to", query: to } });
    const body = await response.json();
    const found = (body.items || []).find((m) => (m.Content.Headers.Subject || []).join("").includes(subjectPart));
    if (found) return found;
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  return null;
}
