import jwt from "jsonwebtoken";
import bcrypt from "bcrypt";
import validator from "validator";
import userModel from "../models/userModel.js";

// The role is part of the token so admin checks need no database lookup.
// Tokens now expire instead of being valid forever.
const createToken = (user) =>
  jwt.sign(
    { id: user._id, role: user.role || "user" },
    process.env.JWT_SECRET,
    {
      expiresIn: "7d",
    },
  );

//login user
const loginUser = async (req, res) => {
  const { email, password } = req.body;
  if (typeof email !== "string" || typeof password !== "string") {
    return res
      .status(400)
      .json({ success: false, message: "Email and password are required" });
  }
  try {
    const user = await userModel.findOne({ email });
    const isMatch = user
      ? await bcrypt.compare(password, user.password)
      : false;

    // Same message for "no such user" and "wrong password", so the login
    // form cannot be used to find out which emails are registered.
    if (!isMatch) {
      return res
        .status(401)
        .json({ success: false, message: "Invalid email or password" });
    }

    res.json({
      success: true,
      token: createToken(user),
      role: user.role || "user",
    });
  } catch (error) {
    console.error(error);
    res.status(500).json({ success: false, message: "Error" });
  }
};

//register user
const registerUser = async (req, res) => {
  const { name, email, password } = req.body;
  if (
    typeof name !== "string" ||
    typeof email !== "string" ||
    typeof password !== "string"
  ) {
    return res.status(400).json({
      success: false,
      message: "Name, email and password are required",
    });
  }
  try {
    // validating email format & strong password
    if (!validator.isEmail(email)) {
      return res
        .status(400)
        .json({ success: false, message: "Please enter a valid email" });
    }
    if (password.length < 8) {
      return res
        .status(400)
        .json({ success: false, message: "Please enter a strong password" });
    }

    const exists = await userModel.findOne({ email });
    if (exists) {
      return res
        .status(409)
        .json({ success: false, message: "User already exists" });
    }

    const salt = await bcrypt.genSalt(10);
    const hashedPassword = await bcrypt.hash(password, salt);

    // role is never read from the request: everyone registers as "user".
    const user = await userModel.create({
      name,
      email,
      password: hashedPassword,
    });
    res
      .status(201)
      .json({ success: true, token: createToken(user), role: user.role });
  } catch (error) {
    console.error(error);
    res.status(500).json({ success: false, message: "Error" });
  }
};

export { loginUser, registerUser };
