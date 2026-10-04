import mongoose from "mongoose";

const userSchema = new mongoose.Schema(
  {
    name: { type: String, required: true },
    email: { type: String, required: true, unique: true },
    password: { type: String, required: true },
    // Only scripts/makeAdmin.js can promote a user to admin.
    role: { type: String, enum: ["user", "admin"], default: "user" },
    cartData: { type: Object, default: {} },
  },
  { minimize: false },
);

const userModel = mongoose.models.user || mongoose.model("user", userSchema);
export default userModel;
