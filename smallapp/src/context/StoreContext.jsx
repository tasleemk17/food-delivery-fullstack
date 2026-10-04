import { createContext, useEffect, useState } from "react";
import { menu_list } from "../assets/assets";
import api, { API_URL } from "../api";

export const StoreContext = createContext(null);

const StoreContextProvider = (props) => {
  // Used for image URLs (`${url}/images/...`). API calls go through ../api.js.
  const url = API_URL;
  const [food_list, setFoodList] = useState([]);
  const [cartItems, setCartItems] = useState({});
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

  // Bug fix: this used to send `{ headers: token }` (the token string as the
  // whole headers object), so the token never reached the server. The api
  // client now attaches the token automatically.
  const loadCartData = async () => {
    const response = await api.post("/api/cart/get", {});
    setCartItems(response.data.cartData || {});
  };

  useEffect(() => {
    async function loadData() {
      await fetchFoodList();
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
    return () => window.removeEventListener("auth:logout", onLogout);
  }, []);

  const contextValue = {
    url,
    food_list,
    menu_list,
    cartItems,
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
