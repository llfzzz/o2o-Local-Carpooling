import React from "react";

/**
 * Free Joy — Alert
 * Inline message banner. tones: info | success | warn | danger | neutral.
 */
export function Alert({ tone = "info", title, icon, onClose, style, children, ...rest }) {
  const tones = {
    info:    ["var(--info-100)", "var(--info-700)", "var(--info-500)"],
    success: ["var(--success-100)", "var(--success-700)", "var(--success-500)"],
    warn:    ["var(--warn-100)", "var(--warn-700)", "var(--warn-500)"],
    danger:  ["var(--danger-100)", "var(--danger-700)", "var(--danger-500)"],
    neutral: ["var(--paper-2)", "var(--ink-2)", "var(--ink-3)"],
  };
  const [bg, fg, accent] = tones[tone] || tones.info;
  return (
    <div
      role="status"
      style={{
        display: "flex", alignItems: "flex-start", gap: 12,
        padding: "13px 15px",
        background: bg,
        border: `1px solid ${accent}33`,
        borderRadius: "var(--radius-md)",
        ...style,
      }}
      {...rest}
    >
      {icon && <span style={{ color: accent, display: "inline-flex", marginTop: 1, flex: "none" }}>{icon}</span>}
      <div style={{ flex: 1, minWidth: 0 }}>
        {title && <div style={{ fontWeight: "var(--weight-semibold)", color: fg, fontSize: "var(--text-sm)", marginBottom: children ? 3 : 0 }}>{title}</div>}
        {children && <div style={{ color: fg, fontSize: "var(--text-sm)", lineHeight: "var(--leading-normal)", opacity: 0.92 }}>{children}</div>}
      </div>
      {onClose && (
        <button
          onClick={onClose} aria-label="Dismiss"
          style={{ border: "none", background: "transparent", cursor: "pointer", color: fg, opacity: 0.6, padding: 2, display: "inline-flex", flex: "none" }}
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round">
            <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>
      )}
    </div>
  );
}
