import React, { useState } from "react";
import { toast } from "react-toastify";
import api, { ADMIN_TOKEN_KEY } from "../../api";
import "./Login.css";

// The admin panel used to have no login at all. Admins now sign in with a
// normal account that has been promoted with `npm run make-admin <email>`.
const Login = ({ onLogin }) => {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const onSubmit = async (e) => {
    e.preventDefault();
    const response = await api.post("/api/user/login", { email, password });
    if (!response.data.success) {
      toast.error(response.data.message || "Login failed");
      return;
    }
    if (response.data.role !== "admin") {
      toast.error("This account does not have admin access");
      return;
    }
    localStorage.setItem(ADMIN_TOKEN_KEY, response.data.token);
    onLogin(response.data.token);
  };

  return (
    <div className="admin-login">
      <form onSubmit={onSubmit} className="admin-login-form">
        <h2>Admin Login</h2>
        <input
          type="email"
          placeholder="Email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
        <input
          type="password"
          placeholder="Password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
        <button type="submit">Login</button>
      </form>
    </div>
  );
};

export default Login;
