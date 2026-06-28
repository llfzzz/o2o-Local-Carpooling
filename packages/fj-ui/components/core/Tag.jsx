import React from "react";

const ACCENTS = {
  coral: ["var(--joy-50)", "var(--joy-700)", "var(--joy-200)"],
  sun:   ["var(--sun-100)", "var(--sun-700)", "var(--sun-300)"],
  bloom: ["var(--bloom-100)", "var(--bloom-700)", "var(--bloom-300)"],
};

/**
 * Free Joy — Tag / Chip
 * Compact label. Optional dot, leading icon, and a remove (×) affordance.
 * `accent` recolors it per-instance ("coral" | "sun" | "bloom" | any CSS color).
 */
export function Tag({
  accent = "neutral",
  dot = false,
  icon,
  onRemove,
  selected = false,
  style,
  children,
  ...rest
}) {
  let bg = "var(--paper-2)", fg = "var(--text-muted)", dotc = "var(--ink-3)";
  if (ACCENTS[accent]) {
    [bg, fg, dotc] = ACCENTS[accent];
  } else if (accent !== "neutral") {
    bg = "color-mix(in srgb, " + accent + " 14%, transparent)";
    fg = accent; dotc = accent;
  }
  return (
    <span
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: 6,
        padding: onRemove ? "4px 6px 4px 12px" : "4px 12px",
        fontFamily: "var(--font-text)",
        fontSize: "var(--text-sm)",
        fontWeight: "var(--weight-medium)",
        lineHeight: 1.3,
        borderRadius: "var(--radius-pill)",
        background: bg,
        color: fg,
        border: selected ? "1px solid currentColor" : "1px solid transparent",
        ...style,
      }}
      {...rest}
    >
      {dot && <span style={{ width: 7, height: 7, borderRadius: "50%", background: dotc, flex: "none" }} />}
      {icon}
      {children}
      {onRemove && (
        <button
          onClick={onRemove}
          aria-label="Remove"
          style={{
            display: "inline-flex",
            alignItems: "center",
            justifyContent: "center",
            width: 18,
            height: 18,
            padding: 0,
            border: "none",
            cursor: "pointer",
            borderRadius: "50%",
            background: "transparent",
            color: "inherit",
            opacity: 0.7,
            font: "inherit",
            fontSize: 15,
            lineHeight: 1,
          }}
        >
          ×
        </button>
      )}
    </span>
  );
}
