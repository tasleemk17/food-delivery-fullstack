import express from "express";
import authMiddleware from "../middleware/auth.js";
import adminAuth from "../middleware/adminAuth.js";
import {
  listOrders,
  placeOrder,
  updateStatus,
  userOrders,
  verifyOrder,
  placeOrderCod,
} from "../controllers/orderController.js";

const orderRouter = express.Router();

// Customer routes (logged-in user)
orderRouter.post("/userorders", authMiddleware, userOrders);
orderRouter.post("/place", authMiddleware, placeOrder);
orderRouter.post("/placecod", authMiddleware, placeOrderCod);
orderRouter.post("/verify", authMiddleware, verifyOrder);

// Admin routes
orderRouter.get("/list", authMiddleware, adminAuth, listOrders);
orderRouter.post("/status", authMiddleware, adminAuth, updateStatus);

// The Stripe webhook is registered in app.js because it needs the raw body.

export default orderRouter;
