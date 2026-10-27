import React, { useContext, useEffect, useState } from "react";
import "./AlsoOrdered.css";
import api from "../../api";
import { StoreContext } from "../../context/StoreContext";

// "Customers also ordered": dishes that other customers had in the same order as the dishes in this cart.
const AlsoOrdered = () => {
  const { cartItems, addToCart, url, currency } = useContext(StoreContext);
  const [foods, setFoods] = useState([]);

  // A string, so the effect runs when the dishes change but not when only a quantity changes
  const cartKey = Object.keys(cartItems)
    .filter((id) => cartItems[id] > 0)
    .sort()
    .join(",");

  useEffect(() => {
    if (!cartKey) {
      setFoods([]);
      return;
    }
    let ignore = false;
    api
      .get("/api/ai/recommendations", { params: { foodIds: cartKey, limit: 4 } })
      .then((response) => {
        if (!ignore) setFoods(response.data?.success ? response.data.data : []);
      })
      .catch(() => {
        if (!ignore) setFoods([]); // a nice extra: when it fails, show nothing
      });
    return () => {
      ignore = true;
    };
  }, [cartKey]);

  if (foods.length === 0) return null;

  return (
    <div className="also-ordered">
      <h2>Customers also ordered</h2>
      <div className="also-ordered-list">
        {foods.map((food) => (
          <div className="also-ordered-item" key={food._id}>
            <img src={url + "/images/" + food.image} alt="" />
            <p className="also-ordered-name">{food.name}</p>
            <p className="also-ordered-price">
              {currency}
              {food.price}
            </p>
            <button type="button" onClick={() => addToCart(food._id)}>
              Add
            </button>
          </div>
        ))}
      </div>
    </div>
  );
};

export default AlsoOrdered;
