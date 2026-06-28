import React from "react";

/**
 * Free Joy — Badge (status / count)
 * tones: neutral | accent | success | warn | danger | sun | bloom
 */
export function Badge({ tone = "neutral", solid = false, style, children, ...rest }) {
  const tones = {
    neutral: { soft: ["var(--paper-2)", "var(--ink-2)"], solid: ["var(--ink)", "var(--white)"] },
    accent:  { soft: ["var(--joy-50)", "var(--joy-700)"], solid: ["var(--joy-500)", "var(--white)"] },
    success: { soft: ["var(--success-100)", "var(--success-700)"], solid: ["var(--success-500)", "var(--white)"] },
    warn:    { soft: ["var(--warn-100)", "var(--warn-700)"], solid: ["var(--warn-500)", "var(--white)"] },
    danger:  { soft: ["var(--danger-100)", "var(--danger-700)"], solid: ["var(--danger-500)", "var(--white)"] },
    sun:     { soft: ["var(--sun-100)", "var(--sun-700)"], solid: ["var(--sun-500)", "var(--ink)"] },
    bloom:   { soft: ["var(--bloom-100)", "var(--bloom-700)"], solid: ["var(--bloom-500)", "var(--white)"] },
  };
  const [bg, fg] = (tones[tone] || tones.neutral)[solid ? "solid" : "soft"];
  return (
    <span
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: 6,
        padding: "3px 10px",
        fontFamily: "var(--font-text)",
        fontSize: "var(--text-xs)",
        fontWeight: "var(--weight-semibold)",
        lineHeight: 1.4,
        borderRadius: "var(--radius-pill)",
        background: bg,
        color: fg,
        ...style,
      }}
      {...rest}
    >
      {children}
    </span>
  );
}
