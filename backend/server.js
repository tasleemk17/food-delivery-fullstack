import "dotenv/config";
import app from "./app.js";
import { connectDB } from "./config/db.js";

// Fail fast if a required secret is missing, instead of crashing later
// in the middle of a request.
const required = ["MONGODB_URI", "JWT_SECRET", "STRIPE_SECRET_KEY"];
const missing = required.filter((key) => !process.env[key]);
if (missing.length) {
  console.error(`Missing environment variables: ${missing.join(", ")}`);
  console.error("Copy backend/.env.example to backend/.env and fill them in.");
  process.exit(1);
}

const port = process.env.PORT || 4000;

await connectDB();
app.listen(port, () =>
  console.log(`Server started on http://localhost:${port}`),
);
