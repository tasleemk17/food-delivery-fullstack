import crypto from "crypto";

// Protects /internal/* routes, which only other services (order-service)
// may call. The caller must send the shared key in the X-Internal-Key header.
//
// The gateway also blocks /internal/* from the outside, so this key is a
// second lock: it still protects these routes if someone reaches Node
// directly on port 4000.
const internalAuth = (req, res, next) => {
  const expected = process.env.INTERNAL_API_KEY;
  if (!expected) {
    // Not configured: keep the routes switched off instead of leaving them open
    return res
      .status(503)
      .json({ success: false, message: "Internal API is not configured" });
  }

  const provided = req.get("X-Internal-Key") || "";
  const a = Buffer.from(provided);
  const b = Buffer.from(expected);
  // Constant-time comparison; lengths are checked first because
  // timingSafeEqual throws when the buffers differ in length.
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) {
    return res
      .status(401)
      .json({ success: false, message: "Invalid internal key" });
  }
  next();
};

export default internalAuth;
