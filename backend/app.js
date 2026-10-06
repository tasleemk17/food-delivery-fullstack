import express from "express";
import cors from "cors";
import userRouter from "./routes/userRoute.js";
import foodRouter from "./routes/foodRoute.js";
import cartRouter from "./routes/cartRoute.js";
import orderRouter from "./routes/orderRoute.js";
import internalRouter from "./routes/internalRoute.js";
import { stripeWebhook } from "./controllers/orderController.js";

// The Express app is built here without connecting to the database or
// listening on a port, so tests can import it directly (see tests/).
const app = express();

// Only the customer site and the admin panel may call this API from a browser.
const allowedOrigins = [
  process.env.FRONTEND_URL || "http://localhost:5173",
  process.env.ADMIN_URL || "http://localhost:5174",
];
app.use(cors({ origin: allowedOrigins }));

// Stripe signs the raw request body, so the webhook route must receive the
// body unparsed. It is registered BEFORE express.json() for that reason.
app.post(
  "/api/order/webhook",
  express.raw({ type: "application/json" }),
  stripeWebhook,
);

app.use(express.json({ limit: "100kb" }));

app.use("/api/user", userRouter);
app.use("/api/food", foodRouter);
app.use("/images", express.static("uploads"));
app.use("/api/cart", cartRouter);
app.use("/api/order", orderRouter);

// Service-to-service routes (order-service). Blocked at the gateway and
// protected by INTERNAL_API_KEY.
app.use("/internal", internalRouter);

app.get("/", (req, res) => {
  res.send("API Working");
});

// Unknown routes
app.use((req, res) => {
  res.status(404).json({ success: false, message: "Not found" });
});

// Last-resort error handler: log the details, never leak them to the client.
// eslint-disable-next-line no-unused-vars
app.use((err, req, res, next) => {
  console.error(err);
  res.status(500).json({ success: false, message: "Something went wrong" });
});

export default app;
