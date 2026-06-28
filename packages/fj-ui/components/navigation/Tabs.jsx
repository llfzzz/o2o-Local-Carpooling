import React from "react";

/**
 * Free Joy — Tabs
 * items: [{ id, label }]; controlled via value + onChange.
 */
export function Tabs({ items = [], value, onChange, style, ...rest }) {
  const active = value ?? items[0]?.id;
  return (
    <div
      role="tablist"
      style={{
        display: "inline-flex",
        gap: 4,
        padding: 4,
        background: "var(--paper-2)",
        borderRadius: "var(--radius-pill)",
        ...style,
      }}
      {...rest}
    >
      {items.map((it) => {
        const on = it.id === active;
        return (
          <button
            key={it.id}
            role="tab"
            aria-selected={on}
            onClick={() => onChange && onChange(it.id)}
            style={{
              border: "none",
              cursor: "pointer",
              padding: "8px 18px",
              borderRadius: "var(--radius-pill)",
              fontFamily: "var(--font-text)",
              fontSize: "var(--text-sm)",
              fontWeight: "var(--weight-semibold)",
              background: on ? "var(--surface)" : "transparent",
              color: on ? "var(--text)" : "var(--text-subtle)",
              boxShadow: on ? "var(--shadow-xs)" : "none",
              transition: "all var(--dur-fast) var(--ease-out)",
            }}
          >
            {it.label}
          </button>
        );
      })}
    </div>
  );
}
