import React from "react";

/**
 * Free Joy — EmptyState
 * Centered placeholder for empty lists, no-results, and zero-data screens.
 */
export function EmptyState({ icon = "inbox", title, description, action, compact = false, style, ...rest }) {
  return (
    <div
      style={{
        display: "flex", flexDirection: "column", alignItems: "center", textAlign: "center",
        gap: 6, padding: compact ? "32px 24px" : "56px 32px", ...style,
      }}
      {...rest}
    >
      <div style={{
        display: "flex", alignItems: "center", justifyContent: "center",
        width: compact ? 48 : 64, height: compact ? 48 : 64, marginBottom: 8,
        borderRadius: "var(--radius-pill)", background: "var(--paper-2)",
      }}>
        <span aria-hidden="true" style={{
          width: compact ? 22 : 28, height: compact ? 22 : 28, backgroundColor: "var(--text-subtle)",
          WebkitMaskImage: `url(https://unpkg.com/lucide-static@0.456.0/icons/${icon}.svg)`, maskImage: `url(https://unpkg.com/lucide-static@0.456.0/icons/${icon}.svg)`,
          WebkitMaskRepeat: "no-repeat", maskRepeat: "no-repeat", WebkitMaskPosition: "center", maskPosition: "center", WebkitMaskSize: "contain", maskSize: "contain",
        }} />
      </div>
      {title && <div style={{ fontFamily: "var(--font-display)", fontSize: "var(--text-lg)", fontWeight: "var(--weight-semibold)", color: "var(--text)" }}>{title}</div>}
      {description && <div style={{ fontSize: "var(--text-sm)", color: "var(--text-muted)", maxWidth: 340, lineHeight: 1.5 }}>{description}</div>}
      {action && <div style={{ marginTop: 14 }}>{action}</div>}
    </div>
  );
}
