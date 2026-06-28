import React from "react";

const TREND = { up: ["var(--success-700)", "trending-up"], down: ["var(--danger-700)", "trending-down"], flat: ["var(--text-subtle)", "minus"] };

/**
 * Free Joy — Stat / Metric
 * Single KPI: label, big value, optional delta with trend, and a sublabel.
 */
export function Stat({ label, value, delta, trend, sublabel, icon, style, ...rest }) {
  const [tColor, tIcon] = TREND[trend] || TREND.flat;
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 6, padding: 24, background: "var(--surface)", border: "1px solid var(--border)", borderRadius: "var(--radius-lg)", boxShadow: "var(--shadow-xs)", ...style }} {...rest}>
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12 }}>
        <span style={{ fontFamily: "var(--font-text)", fontSize: "var(--text-sm)", fontWeight: "var(--weight-medium)", color: "var(--text-muted)" }}>{label}</span>
        {icon && <span aria-hidden="true" style={{
          width: 18, height: 18, backgroundColor: "var(--text-subtle)",
          WebkitMaskImage: `url(https://unpkg.com/lucide-static@0.456.0/icons/${icon}.svg)`, maskImage: `url(https://unpkg.com/lucide-static@0.456.0/icons/${icon}.svg)`,
          WebkitMaskRepeat: "no-repeat", maskRepeat: "no-repeat", WebkitMaskPosition: "center", maskPosition: "center", WebkitMaskSize: "contain", maskSize: "contain",
        }} />}
      </div>
      <div style={{ display: "flex", alignItems: "baseline", gap: 10, flexWrap: "wrap" }}>
        <span style={{ fontFamily: "var(--font-display)", fontSize: "var(--text-2xl)", fontWeight: "var(--weight-bold)", letterSpacing: "var(--tracking-tight)", color: "var(--text)", lineHeight: 1 }}>{value}</span>
        {delta != null && (
          <span style={{ display: "inline-flex", alignItems: "center", gap: 3, fontFamily: "var(--font-text)", fontSize: "var(--text-sm)", fontWeight: "var(--weight-semibold)", color: tColor }}>
            <span aria-hidden="true" style={{
              width: 15, height: 15, backgroundColor: "currentColor",
              WebkitMaskImage: `url(https://unpkg.com/lucide-static@0.456.0/icons/${tIcon}.svg)`, maskImage: `url(https://unpkg.com/lucide-static@0.456.0/icons/${tIcon}.svg)`,
              WebkitMaskRepeat: "no-repeat", maskRepeat: "no-repeat", WebkitMaskPosition: "center", maskPosition: "center", WebkitMaskSize: "contain", maskSize: "contain",
            }} />
            {delta}
          </span>
        )}
      </div>
      {sublabel && <span style={{ fontSize: "var(--text-xs)", color: "var(--text-subtle)" }}>{sublabel}</span>}
    </div>
  );
}
