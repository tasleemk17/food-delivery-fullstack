import mongoose from "mongoose";
import userModel from "../models/userModel.js";
import foodModel from "../models/foodModel.js";

// Endpoints used by order-service. Node still owns users, carts and the food
// catalogue; order-service asks for what it needs to build an order.

const findUser = async (req, res) => {
  const { userId } = req.params;
  if (!mongoose.isValidObjectId(userId)) {
    res.status(400).json({ success: false, message: "Invalid user id" });
    return null;
  }
  const user = await userModel.findById(userId);
  if (!user) {
    res.status(404).json({ success: false, message: "User not found" });
    return null;
  }
  return user;
};

/**
 * GET /internal/users/:userId/cart
 * Returns the saved cart plus the current name and price of every food in it,
 * so order-service can price the order from database values.
 */
const getCartWithPrices = async (req, res) => {
  try {
    const user = await findUser(req, res);
    if (!user) return;

    const cart = Object.fromEntries(
      Object.entries(user.cartData || {}).filter(([, qty]) => qty > 0),
    );
    const foodIds = Object.keys(cart).filter((id) =>
      mongoose.isValidObjectId(id),
    );
    const foods = await foodModel.find({ _id: { $in: foodIds } });

    res.json({
      success: true,
      cart,
      foods: foods.map((food) => ({
        id: String(food._id),
        name: food.name,
        price: food.price,
      })),
    });
  } catch (error) {
    console.error(error);
    res.status(500).json({ success: false, message: "Error" });
  }
};

/**
 * POST /internal/users/:userId/cart/clear
 * Called by order-service after an order is placed or paid.
 */
const clearCart = async (req, res) => {
  try {
    const user = await findUser(req, res);
    if (!user) return;

    await userModel.findByIdAndUpdate(user._id, { cartData: {} });
    res.json({ success: true });
  } catch (error) {
    console.error(error);
    res.status(500).json({ success: false, message: "Error" });
  }
};

export { getCartWithPrices, clearCart };
