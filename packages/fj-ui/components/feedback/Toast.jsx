import React from "react";
import { iconMask } from "../core/iconMask.js";

const TONES = {
  neutral: ["var(--ink)", "var(--white)", "info"],
  success: ["var(--success-500)", "var(--white)", "check-circle"],
  warn:    ["var(--warn-500)", "var(--white)", "alert-triangle"],
  danger:  ["var(--danger-500)", "var(--white)", "alert-circle"],
  info:    ["var(--info-500)", "var(--white)", "info"],
};

const ToastContext = React.createContext(null);

/** Hook to push toasts from anywhere inside <ToastProvider>. */
export function useToast() {
  const ctx = React.useContext(ToastContext);
  return ctx ? ctx.push : () => {};
}

/**
 * Free Joy — Toast / ToastProvider
 * Wrap your app in <ToastProvider>; call useToast()(opts) to show a toast.
 * opts: { title, description, tone, duration, icon }
 */
export function ToastProvider({ position = "bottom-right", children }) {
  const [items, setItems] = React.useState([]);
  const push = React.useCallback((opts) => {
    const id = Math.random().toString(36).slice(2);
    const t = { id, tone: "neutral", duration: 4000, ...(typeof opts === "string" ? { title: opts } : opts) };
    setItems((s) => [...s, t]);
    if (t.duration > 0) setTimeout(() => setItems((s) => s.filter((x) => x.id !== id)), t.duration);
    return id;
  }, []);
  const dismiss = (id) => setItems((s) => s.filter((x) => x.id !== id));

  const corner = {
    "bottom-right": { bottom: 24, right: 24, alignItems: "flex-end" },
    "bottom-left": { bottom: 24, left: 24, alignItems: "flex-start" },
    "top-right": { top: 24, right: 24, alignItems: "flex-end" },
    "top-left": { top: 24, left: 24, alignItems: "flex-start" },
  }[position];
  const fromTop = position.startsWith("top");

  return (
    <ToastContext.Provider value={{ push, dismiss }}>
      {children}
      <div style={{ position: "fixed", display: "flex", flexDirection: "column", gap: 12, zIndex: 200, pointerEvents: "none", ...corner }}>
        {items.map((t) => <Toast key={t.id} {...t} fromTop={fromTop} onClose={() => dismiss(t.id)} />)}
      </div>
      <style dangerouslySetInnerHTML={{ __html: "@keyframes fj-toast-in{from{opacity:0;transform:translateY(var(--fj-from,12px)) scale(0.98)}to{opacity:1;transform:translateY(0) scale(1)}}" }} />
    </ToastContext.Provider>
  );
}

/**
 * Free Joy — Toast (standalone notification card)
 */
export function Toast({ title, description, tone = "neutral", icon, onClose, fromTop = false, style }) {
  const [accent, , defIcon] = TONES[tone] || TONES.neutral;
  const iconName = icon || defIcon;
  return (
    <div
      role="status"
      style={{
        display: "flex", alignItems: "flex-start", gap: 12, width: 340, maxWidth: "calc(100vw - 48px)",
        padding: "14px 16px", pointerEvents: "auto",
        background: "var(--glass-bg-strong)",
        backdropFilter: "blur(var(--glass-blur-lg)) saturate(180%)",
        WebkitBackdropFilter: "blur(var(--glass-blur-lg)) saturate(180%)",
        border: "1px solid var(--glass-border)",
        borderRadius: "var(--radius-lg)", boxShadow: "var(--shadow-lg)",
        animation: "fj-toast-in var(--dur-base) var(--ease-out)",
        "--fj-from": fromTop ? "-12px" : "12px",
        ...style,
      }}
    >
      <span aria-hidden="true" style={{
        width: 20, height: 20, flex: "none", marginTop: 1, backgroundColor: accent,
        WebkitMaskImage: iconMask(iconName),
        maskImage: iconMask(iconName),
        WebkitMaskRepeat: "no-repeat", maskRepeat: "no-repeat", WebkitMaskPosition: "center", maskPosition: "center", WebkitMaskSize: "contain", maskSize: "contain",
      }} />
      <div style={{ flex: 1, minWidth: 0 }}>
        {title && <div style={{ fontFamily: "var(--font-text)", fontSize: "var(--text-sm)", fontWeight: "var(--weight-semibold)", color: "var(--text)" }}>{title}</div>}
        {description && <div style={{ fontSize: "var(--text-sm)", color: "var(--text-muted)", marginTop: 2, lineHeight: 1.45 }}>{description}</div>}
      </div>
      {onClose && (
        <button onClick={onClose} aria-label="Dismiss" style={{ flex: "none", width: 22, height: 22, border: "none", background: "transparent", cursor: "pointer", color: "var(--text-subtle)", fontSize: 16 }}>×</button>
      )}
    </div>
  );
}
