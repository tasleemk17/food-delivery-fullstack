import React, { useContext, useEffect } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { toast } from "react-toastify";
import { StoreContext } from "../../Context/StoreContext";
import api from "../../api";
import "./Verify.css";

const Verify = () => {
  const [searchParams] = useSearchParams();
  const orderId = searchParams.get("orderId");
  const { setCartItems } = useContext(StoreContext);
  const navigate = useNavigate();

  // Only the order id is sent. The server checks the payment with Stripe
  // itself; the browser can no longer claim "success=true".
  const verifyPayment = async () => {
    const response = await api.post("/api/order/verify", { orderId });
    if (response.data.success) {
      setCartItems({});
      navigate("/myorders");
    } else {
      toast.error("Payment not completed");
      navigate("/cart");
    }
  };

  useEffect(() => {
    verifyPayment();
  }, []);

  return (
    <div className="verify">
      <div className="spinner"></div>
    </div>
  );
};

export default Verify;
