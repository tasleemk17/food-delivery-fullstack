import jwt from "jsonwebtoken";

// Reads the JWT from the "token" header (used by the existing frontend)
// or from a standard "Authorization: Bearer <jwt>" header.
const readToken = (req) => {
  if (req.headers.token) return req.headers.token;
  const header = req.headers.authorization || "";
  return header.startsWith("Bearer ") ? header.slice(7) : null;
};

const authMiddleware = (req, res, next) => {
  const token = readToken(req);
  if (!token) {
    return res
      .status(401)
      .json({ success: false, message: "Not authorized, please log in again" });
  }
  try {
    const decoded = jwt.verify(token, process.env.JWT_SECRET);
    // Identity is attached to req, not req.body, so a client can never
    // supply or overwrite it.
    req.userId = decoded.id;
    req.userRole = decoded.role || "user";
    next();
  } catch {
    return res.status(401).json({
      success: false,
      message: "Session expired, please log in again",
    });
  }
};

export default authMiddleware;
