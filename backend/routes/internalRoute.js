import express from "express";
import internalAuth from "../middleware/internalAuth.js";
import {
  getCartWithPrices,
  clearCart,
} from "../controllers/internalController.js";

const internalRouter = express.Router();

// Every route here needs the internal key
internalRouter.use(internalAuth);

internalRouter.get("/users/:userId/cart", getCartWithPrices);
internalRouter.post("/users/:userId/cart/clear", clearCart);

export default internalRouter;
