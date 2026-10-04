import express from "express";
import multer from "multer";
import path from "path";
import {
  addFood,
  listFood,
  removeFood,
} from "../controllers/foodController.js";
import authMiddleware from "../middleware/auth.js";
import adminAuth from "../middleware/adminAuth.js";

const foodRouter = express.Router();

// Image Storage Engine (saving image to uploads folder with a safe name)
const storage = multer.diskStorage({
  destination: "uploads",
  filename: (req, file, cb) => {
    const ext = path.extname(file.originalname).toLowerCase();
    cb(null, `${Date.now()}-${Math.round(Math.random() * 1e6)}${ext}`);
  },
});

const upload = multer({
  storage,
  limits: { fileSize: 2 * 1024 * 1024 }, // 2 MB
  fileFilter: (req, file, cb) =>
    cb(null, /^image\/(png|jpe?g|webp)$/.test(file.mimetype)),
});

foodRouter.get("/list", listFood);
// Auth runs before multer, so files from non-admins are never saved to disk.
foodRouter.post(
  "/add",
  authMiddleware,
  adminAuth,
  upload.single("image"),
  addFood,
);
foodRouter.post("/remove", authMiddleware, adminAuth, removeFood);

export default foodRouter;
