import React from "react";
import "./Navbar.css";
import { assets } from "../../assets/assets";

const Navbar = ({ onLogout }) => {
  return (
    <div className="navbar">
      <img className="logo" src={assets.logo} alt="" />
      <div style={{ display: "flex", alignItems: "center", gap: "16px" }}>
        <button
          onClick={onLogout}
          style={{ padding: "6px 14px", cursor: "pointer" }}
        >
          Logout
        </button>
        <img className="profile" src={assets.profile_image} alt="" />
      </div>
    </div>
  );
};

export default Navbar;
