// Must run after authMiddleware. Lets the request through only for admins.
const adminAuth = (req, res, next) => {
  if (req.userRole !== "admin") {
    return res
      .status(403)
      .json({ success: false, message: "Admin access only" });
  }
  next();
};

export default adminAuth;
