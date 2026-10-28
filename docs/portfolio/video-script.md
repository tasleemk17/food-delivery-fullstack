# Demo video script (about 2 minutes)

Record the screen with **Win + G** (Xbox Game Bar > Capture) or OBS, and speak in your own words. The lines below are a guide, not a text to read out. Use a fresh test account (`@tomato.test`), never your real name, address or phone.

Before recording: `docker compose up -d`, wait one minute, open http://localhost:5173. Close other tabs and notifications.

| Time | What is on screen | What to say |
|---|---|---|
| 0:00 | Home page | "This is Tomato, a food delivery app I built end to end. Behind this simple website there are several small services that talk to each other." |
| 0:15 | Scroll the menu | "The menu comes from a Node.js backend with MongoDB. Dishes that are out of stock show a badge, which comes from a separate stock service." |
| 0:30 | Sign up, add 2 dishes, open the cart | "I sign up and add two dishes. See this part, 'Customers also ordered'? A Python service learned it from earlier orders." |
| 0:50 | Checkout, Cash on delivery, Place Order | "I place the order with cash on delivery. Stripe card payment works too, in test mode." |
| 1:05 | My Orders | "The order is saved in PostgreSQL by a Java Spring Boot order service, behind an API gateway that checks my login." |
| 1:15 | MailHog (localhost:8025), open the email | "At the same time the order became an event on Kafka. A notification service picked it up and sent this confirmation email, and the stock service reserved the stock. These services never call each other directly." |
| 1:40 | Open the chat widget, ask "Which salads do you have?" | "This assistant answers from the real menu and help pages (RAG). With a Claude API key it writes the answer; without one it still works from search, so it never breaks." |
| 1:55 | Admin panel Orders page (blur or avoid real data) | "The admin can change the order status and manage the stock." |
| 2:05 | GitHub repo page | "All of it runs with Docker Compose, has over 300 automated tests, a CI pipeline, and I deployed it on an AWS EC2 server. The code is on my GitHub." |

Tips:
- Speak slowly, pause when a page loads. One short sentence per scene is enough.
- Do not show the admin Orders page with a real customer's name, address or phone number.
- Upload the video to YouTube as **Unlisted** and put the link in your portfolio, README and LinkedIn.
