import { createContext, useEffect, useState } from "react";
import { menu_list } from "../assets/assets";
import api, { API_URL } from "../api";

export const StoreContext = createContext(null);

const StoreContextProvider = (props) => {
  // Used for image URLs (`${url}/images/...`). API calls go through ../api.js.
  const url = API_URL;
  const [food_list, setFoodList] = useState([]);
  const [cartItems, setCartItems] = useState({});
  // foodId -> true/false for foods whose stock is tracked. A food that is not
  // in this list is always available.
  const [availability, setAvailability] = useState({});
  const [token, setToken] = useState("");
  const currency = "₹";
  const deliveryCharge = 50;

  const addToCart = async (itemId) => {
    setCartItems((prev) => ({ ...prev, [itemId]: (prev[itemId] || 0) + 1 }));
    if (token) {
      await api.post("/api/cart/add", { itemId });
    }
  };

  const removeFromCart = async (itemId) => {
    setCartItems((prev) => ({
      ...prev,
      [itemId]: Math.max((prev[itemId] || 0) - 1, 0),
    }));
    if (token) {
      await api.post("/api/cart/remove", { itemId });
    }
  };

  // Display only. The server recalculates the real total when an order is placed.
  const getTotalCartAmount = () => {
    let totalAmount = 0;
    for (const item in cartItems) {
      if (cartItems[item] > 0) {
        const itemInfo = food_list.find((product) => product._id === item);
        if (itemInfo) totalAmount += itemInfo.price * cartItems[item];
      }
    }
    return totalAmount;
  };

  const fetchFoodList = async () => {
    const response = await api.get("/api/food/list");
    setFoodList(response.data.data || []);
  };

  // Only for showing "Out of stock" on the menu. If this call fails the menu
  // simply shows everything as available; the server still checks the stock
  // when the order is placed, so nothing can be oversold.
  const fetchAvailability = async () => {
    try {
      const response = await api.get("/api/inventory/availability");
      if (response.data.success) setAvailability(response.data.data || {});
    } catch {
      /* keep what we have */
    }
  };

  const isOutOfStock = (itemId) => availability[itemId] === false;

  // Bug fix: this used to send `{ headers: token }` (the token string as the
  // whole headers object), so the token never reached the server. The api
  // client now attaches the token automatically.
  const loadCartData = async () => {
    const response = await api.post("/api/cart/get", {});
    setCartItems(response.data.cartData || {});
  };

  useEffect(() => {
    async function loadData() {
      await Promise.all([fetchFoodList(), fetchAvailability()]);
      if (localStorage.getItem("token")) {
        setToken(localStorage.getItem("token"));
        await loadCartData();
      }
    }
    loadData();

    // Fired by api.js when the server says the login has expired.
    const onLogout = () => {
      setToken("");
      setCartItems({});
    };
    window.addEventListener("auth:logout", onLogout);

    // Stock changes while the page stays open: look again when the tab is shown
    const onVisible = () => {
      if (document.visibilityState === "visible") fetchAvailability();
    };
    document.addEventListener("visibilitychange", onVisible);

    return () => {
      window.removeEventListener("auth:logout", onLogout);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, []);

  const contextValue = {
    url,
    food_list,
    menu_list,
    cartItems,
    isOutOfStock,
    fetchAvailability,
    addToCart,
    removeFromCart,
    getTotalCartAmount,
    token,
    setToken,
    loadCartData,
    setCartItems,
    currency,
    deliveryCharge,
  };

  return (
    <StoreContext.Provider value={contextValue}>
      {props.children}
    </StoreContext.Provider>
  );
};

export default StoreContextProvider;
