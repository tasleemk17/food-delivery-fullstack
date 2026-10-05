import mongoose from "mongoose";
import userModel from "../models/userModel.js";
import foodModel from "../models/foodModel.js";

/**
 * Removes cart entries whose food no longer exists (deleted from the menu)
 * or whose quantity is 0, and saves the cleaned cart.
 *
 * Why: the customer site only shows cart items that are still on the menu.
 * A deleted food would stay hidden in the saved cart, the user could not see
 * or remove it, and every checkout would fail on it.
 *
 * @returns {{ cartData: Object, removed: number }}
 */
export const removeUnavailableItems = async (user) => {
  const cartData = user.cartData || {};
  const wanted = Object.entries(cartData).filter(([, qty]) => qty > 0);
  const validIds = wanted
    .map(([id]) => id)
    .filter((id) => mongoose.isValidObjectId(id));

  const existing = await foodModel.find({ _id: { $in: validIds } }, "_id");
  const stillOnMenu = new Set(existing.map((food) => String(food._id)));

  const cleaned = Object.fromEntries(
    wanted.filter(([id]) => stillOnMenu.has(id)),
  );
  const removed = Object.keys(cartData).length - Object.keys(cleaned).length;

  if (removed > 0) {
    await userModel.findByIdAndUpdate(user._id, { cartData: cleaned });
  }
  return { cartData: cleaned, removed };
};
