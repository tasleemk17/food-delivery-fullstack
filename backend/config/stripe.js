import Stripe from "stripe";

// One shared Stripe client. Kept in its own module so tests can replace it
// with a mock instead of calling the real Stripe API.
const stripe = new Stripe(process.env.STRIPE_SECRET_KEY || "sk_test_missing");

export default stripe;
