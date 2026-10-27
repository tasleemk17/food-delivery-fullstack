import React, { useEffect, useState } from "react";
import "./Stock.css";
import { url } from "../../assets/assets";
import api from "../../api";
import { toast } from "react-toastify";

const MAX_STOCK = 1000000;
const LOW_STOCK = 5;

// A food with no stock row is "not tracked": customers can always order it.
// Setting a number starts tracking it; "Stop tracking" makes it unlimited again.
const Stock = () => {
  const [foods, setFoods] = useState([]);
  const [stock, setStock] = useState({}); // foodId -> units left (tracked foods only)
  const [inputs, setInputs] = useState({}); // foodId -> what the admin has typed
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState(null);

  const load = async () => {
    try {
      const [foodRes, stockRes] = await Promise.all([
        api.get("/api/food/list"),
        api.get("/api/inventory"),
      ]);
      if (!foodRes.data.success || !stockRes.data.success) {
        toast.error(stockRes.data.message || foodRes.data.message || "Error");
        return;
      }
      const levels = {};
      stockRes.data.data.forEach((s) => {
        levels[s.foodId] = s.quantity;
      });
      setFoods(foodRes.data.data);
      setStock(levels);
      setInputs({});
    } catch {
      toast.error("Could not load the stock. Is the server running?");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const typed = (id) => (id in inputs ? inputs[id] : (stock[id] ?? ""));
  const isTracked = (id) => id in stock;
  const hasChange = (id) => id in inputs && inputs[id] !== String(stock[id] ?? "");

  const onType = (id, value) => {
    // digits only, so the field can never hold a negative or a fraction
    if (/^\d*$/.test(value)) setInputs((prev) => ({ ...prev, [id]: value }));
  };

  const save = async (id) => {
    const text = typed(id);
    if (text === "") {
      toast.error("Type how many are in stock");
      return;
    }
    const quantity = Number(text);
    if (quantity > MAX_STOCK) {
      toast.error(`Stock can be at most ${MAX_STOCK}`);
      return;
    }
    setBusyId(id);
    try {
      const response = await api.put(`/api/inventory/${encodeURIComponent(id)}`, { quantity });
      if (response.data.success) {
        toast.success("Stock saved");
        await load();
      } else {
        toast.error(response.data.message || "Error");
      }
    } catch {
      toast.error("Could not save the stock");
    } finally {
      setBusyId(null);
    }
  };

  const stopTracking = async (id) => {
    setBusyId(id);
    try {
      const response = await api.delete(`/api/inventory/${encodeURIComponent(id)}`);
      if (response.data.success) {
        toast.success("Stock tracking stopped. This food is always available.");
        await load();
      } else {
        toast.error(response.data.message || "Error");
      }
    } catch {
      toast.error("Could not stop tracking");
    } finally {
      setBusyId(null);
    }
  };

  const status = (id) => {
    if (!isTracked(id)) return <span className="stock-badge untracked">Not tracked</span>;
    const left = stock[id];
    if (left === 0) return <span className="stock-badge out">Out of stock</span>;
    if (left <= LOW_STOCK) return <span className="stock-badge low">Low: {left} left</span>;
    return <span className="stock-badge ok">In stock: {left}</span>;
  };

  return (
    <div className="stock add flex-col">
      <p>Stock</p>
      <p className="stock-help">
        Type how many units are available and press Save. A food that is not tracked is always
        available. When an order is placed its units are held, and they go back if the payment fails.
      </p>
      <div className="stock-table">
        <div className="stock-table-format title">
          <b>Image</b>
          <b>Name</b>
          <b>Category</b>
          <b>Status</b>
          <b>Units in stock</b>
          <b>Action</b>
        </div>
        {loading && <p className="stock-empty">Loading...</p>}
        {!loading && foods.length === 0 && <p className="stock-empty">No foods yet. Add one first.</p>}
        {foods.map((item) => (
          <div key={item._id} className="stock-table-format">
            <img src={`${url}/images/` + item.image} alt="" />
            <p>{item.name}</p>
            <p>{item.category}</p>
            {status(item._id)}
            <input
              type="text"
              inputMode="numeric"
              placeholder="Unlimited"
              value={typed(item._id)}
              onChange={(e) => onType(item._id, e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && hasChange(item._id) && save(item._id)}
              aria-label={`Units of ${item.name} in stock`}
            />
            <div className="stock-actions">
              <button
                onClick={() => save(item._id)}
                disabled={busyId === item._id || !hasChange(item._id)}
              >
                {isTracked(item._id) ? "Save" : "Track"}
              </button>
              {isTracked(item._id) && (
                <button
                  className="secondary"
                  onClick={() => stopTracking(item._id)}
                  disabled={busyId === item._id}
                >
                  Stop tracking
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default Stock;
