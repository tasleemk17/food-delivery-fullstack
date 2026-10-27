import React, { useContext, useEffect, useRef, useState } from "react";
import "./ChatWidget.css";
import api from "../../api";
import { StoreContext } from "../../context/StoreContext";

const MAX_CHARS = 500;
const GREETING = {
  role: "assistant",
  content:
    "Hi! Ask me about our dishes, prices, payments or delivery. Hindi / Hinglish is fine too.",
};

const ChatWidget = ({ setShowLogin }) => {
  const { token } = useContext(StoreContext);
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState([GREETING]);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const bottomRef = useRef(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, open, busy]);

  const addMessage = (message) => setMessages((prev) => [...prev, message]);

  const send = async (event) => {
    event.preventDefault();
    const question = text.trim();
    if (!question || busy) return;

    // The greeting and error notes are not part of the conversation the server should see
    const history = messages
      .slice(1)
      .filter((m) => !m.error)
      .map(({ role, content }) => ({ role, content }));
    addMessage({ role: "user", content: question });
    setText("");
    setBusy(true);
    try {
      const response = await api.post("/api/ai/chat", { message: question, history });
      const body = response.data;
      if (response.status === 200 && body.success) {
        addMessage({
          role: "assistant",
          content: body.data.answer,
          sources: body.data.sources,
          searchOnly: body.data.mode === "search",
        });
      } else if (response.status === 401) {
        addMessage({ role: "assistant", content: "Please log in to chat with me.", error: true });
      } else {
        addMessage({
          role: "assistant",
          content: body?.message || "Something went wrong. Please try again.",
          error: true,
        });
      }
    } catch {
      addMessage({
        role: "assistant",
        content: "I could not reach the server. Please try again.",
        error: true,
      });
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="chat-widget">
      {open && (
        <div className="chat-panel" role="dialog" aria-label="Food assistant">
          <div className="chat-header">
            <span>Tomato assistant</span>
            <button type="button" onClick={() => setOpen(false)} aria-label="Close chat">
              x
            </button>
          </div>
          <div className="chat-messages">
            {messages.map((m, i) => (
              <div key={i} className={`chat-message ${m.role}${m.error ? " error" : ""}`}>
                <p>{m.content}</p>
                {m.sources?.length > 0 && (
                  <small className="chat-sources">
                    Based on: {[...new Set(m.sources.map((s) => s.title))].slice(0, 4).join(", ")}
                  </small>
                )}
                {m.searchOnly && (
                  <small className="chat-sources">Search result (AI writing is switched off)</small>
                )}
              </div>
            ))}
            {busy && <div className="chat-message assistant chat-typing">Thinking...</div>}
            <div ref={bottomRef} />
          </div>
          {token ? (
            <form className="chat-input" onSubmit={send}>
              <input
                value={text}
                onChange={(e) => setText(e.target.value)}
                maxLength={MAX_CHARS}
                placeholder="Ask about the menu..."
                aria-label="Your question"
              />
              <button type="submit" disabled={busy || !text.trim()}>
                Send
              </button>
            </form>
          ) : (
            <div className="chat-login">
              <p>Log in to chat with the assistant.</p>
              <button
                type="button"
                onClick={() => {
                  setOpen(false);
                  setShowLogin(true);
                }}
              >
                Sign in
              </button>
            </div>
          )}
        </div>
      )}
      <button
        type="button"
        className="chat-toggle"
        onClick={() => setOpen((o) => !o)}
        aria-label={open ? "Close chat" : "Open chat"}
      >
        {open ? "x" : "Ask us"}
      </button>
    </div>
  );
};

export default ChatWidget;
