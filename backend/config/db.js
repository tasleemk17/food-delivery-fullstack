import mongoose from "mongoose";

export const connectDB = async () => {
  await mongoose
    .connect(
      process.env.MONGODB_URI,
      // "mongodb+srv://greatstack:greatstack123@cluster0.fsx7g6x.mongodb.net/food-del",
    )
    .then(() => console.log("DB Connected"));

  //     // The connection string now comes from the environment (backend/.env),
  // // so no database password is ever committed to Git.
  // export const connectDB = async () => {
  //   await mongoose.connect(process.env.MONGODB_URI);
  //   console.log("DB Connected");
};
