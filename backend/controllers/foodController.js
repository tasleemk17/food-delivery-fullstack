import fs from "fs";
import foodModel from "../models/foodModel.js";

// all food list
const listFood = async (req, res) => {
  try {
    const foods = await foodModel.find({});
    res.json({ success: true, data: foods });
  } catch (error) {
    console.error(error);
    res.status(500).json({ success: false, message: "Error" });
  }
};

// add food (admin)
const addFood = async (req, res) => {
  if (!req.file) {
    return res
      .status(400)
      .json({ success: false, message: "Image is required" });
  }
  const price = Number(req.body.price);
  if (!Number.isFinite(price) || price <= 0) {
    fs.unlink(req.file.path, () => {});
    return res
      .status(400)
      .json({ success: false, message: "Price must be a positive number" });
  }
  try {
    await foodModel.create({
      name: req.body.name,
      description: req.body.description,
      price,
      category: req.body.category,
      image: req.file.filename,
    });
    res.status(201).json({ success: true, message: "Food Added" });
  } catch (error) {
    console.error(error);
    fs.unlink(req.file.path, () => {});
    res.status(400).json({ success: false, message: "Could not add food" });
  }
};

// delete food (admin)
const removeFood = async (req, res) => {
  try {
    const food = await foodModel.findByIdAndDelete(req.body.id);
    if (!food) {
      return res
        .status(404)
        .json({ success: false, message: "Food not found" });
    }
    fs.unlink(`uploads/${food.image}`, () => {});
    res.json({ success: true, message: "Food Removed" });
  } catch (error) {
    console.error(error);
    res.status(500).json({ success: false, message: "Error" });
  }
};

export { listFood, addFood, removeFood };
