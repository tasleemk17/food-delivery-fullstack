// Promotes an existing account to admin.
// Usage (from the backend folder):  node scripts/makeAdmin.js you@example.com
import "dotenv/config";
import mongoose from "mongoose";
import userModel from "../models/userModel.js";

const email = process.argv[2];
if (!email) {
  console.error("Usage: node scripts/makeAdmin.js <email>");
  process.exit(1);
}

await mongoose.connect(process.env.MONGODB_URI);
const user = await userModel.findOneAndUpdate(
  { email },
  { role: "admin" },
  { new: true },
);
console.log(
  user
    ? `${email} is now an admin. Log in again to get a new token.`
    : `No user with email ${email}`,
);
await mongoose.disconnect();
