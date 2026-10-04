import React, { useEffect, useState } from "react";
import Navbar from "./components/Navbar/Navbar";
import Sidebar from "./components/Sidebar/Sidebar";
import { Route, Routes } from "react-router-dom";
import Add from "./pages/Add/Add";
import List from "./pages/List/List";
import Orders from "./pages/Orders/Orders";
import Login from "./pages/Login/Login";
import { ToastContainer } from "react-toastify";
import "react-toastify/dist/ReactToastify.css";
import { ADMIN_TOKEN_KEY } from "./api";

const App = () => {
  const [token, setToken] = useState(
    localStorage.getItem(ADMIN_TOKEN_KEY) || "",
  );

  // api.js fires this when the server rejects the admin token
  useEffect(() => {
    const onLogout = () => setToken("");
    window.addEventListener("admin:logout", onLogout);
    return () => window.removeEventListener("admin:logout", onLogout);
  }, []);

  const logout = () => {
    localStorage.removeItem(ADMIN_TOKEN_KEY);
    setToken("");
  };

  if (!token) {
    return (
      <>
        <ToastContainer />
        <Login onLogin={setToken} />
      </>
    );
  }

  return (
    <div className="app">
      <ToastContainer />
      <Navbar onLogout={logout} />
      <hr />
      <div className="app-content">
        <Sidebar />
        <Routes>
          <Route path="/add" element={<Add />} />
          <Route path="/list" element={<List />} />
          <Route path="/orders" element={<Orders />} />
        </Routes>
      </div>
    </div>
  );
};

export default App;
