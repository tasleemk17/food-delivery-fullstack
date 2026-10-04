import mongoose from "mongoose";
import userModel from "../models/userModel.js";
import foodModel from "../models/foodModel.js";

const isValidFood = async (itemId) =>
  mongoose.isValidObjectId(itemId) &&
  Boolean(await foodModel.exists({ _id: itemId }));

// add to user cart
const addToCart = async (req, res) => {
  try {
    if (!(await isValidFood(req.body.itemId))) {
      return res
        .status(404)
        .json({ success: false, message: "Food not found" });
    }
    const user = await userModel.findById(req.userId);
    if (!user)
      return res
        .status(404)
        .json({ success: false, message: "User not found" });

    const cartData = user.cartData || {};
    cartData[req.body.itemId] = (cartData[req.body.itemId] || 0) + 1;
    await userModel.findByIdAndUpdate(req.userId, { cartData });
    res.json({ success: true, message: "Added To Cart" });
  } catch (error) {
    console.error(error);
    res.status(500).json({ success: false, message: "Error" });
  }
};

// remove food from user cart
const removeFromCart = async (req, res) => {
  try {
    const user = await userModel.findById(req.userId);
    if (!user)
      return res
        .status(404)
        .json({ success: false, message: "User not found" });

    const cartData = user.cartData || {};
    if (cartData[req.body.itemId] > 0) {
      cartData[req.body.itemId] -= 1;
    }
    await userModel.findByIdAndUpdate(req.userId, { cartData });
    res.json({ success: true, message: "Removed From Cart" });
  } catch (error) {
    console.error(error);
    res.status(500).json({ success: false, message: "Error" });
  }
};

// get user cart
const getCart = async (req, res) => {
  try {
    const user = await userModel.findById(req.userId);
    if (!user)
      return res
        .status(404)
        .json({ success: false, message: "User not found" });
    res.json({ success: true, cartData: user.cartData || {} });
  } catch (error) {
    console.error(error);
    res.status(500).json({ success: false, message: "Error" });
  }
};

export { addToCart, removeFromCart, getCart };
