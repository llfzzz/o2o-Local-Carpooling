import React from "react";

/**
 * Free Joy — SegmentedControl
 * iOS-style segmented control with a sliding thumb. options: [{ value, label, icon }]
 * Segments are equal-width (grid columns), so the thumb is a simple, reliable
 * fraction of the track — it always sits exactly behind the active segment.
 */
export function SegmentedControl({ options = [], value, defaultValue, onChange, size = "md", full = false, style }) {
  const [internal, setInternal] = React.useState(defaultValue ?? options[0]?.value);
  const val = value ?? internal;
  const n = options.length || 1;
  const idx = Math.max(0, options.findIndex((o) => o.value === val));
  const h = { sm: 34, md: 40, lg: 46 }[size] || 40;

  const set = (v) => { if (value === undefined) setInternal(v); onChange && onChange(v); };

  return (
    <div style={{
      position: "relative",
      display: full ? "grid" : "inline-grid",
      gridTemplateColumns: `repeat(${n}, minmax(0, 1fr))`,
      gridAutoColumns: "1fr",
      width: full ? "100%" : undefined,
      padding: 4, height: h, boxSizing: "border-box",
      background: "var(--paper-2)", borderRadius: "var(--radius-pill)", ...style,
    }}>
      {/* Sliding thumb: a clean 1/n slice of the inner track. */}
      <div style={{
        position: "absolute", top: 4, bottom: 4, left: 4,
        width: `calc((100% - 8px) / ${n})`,
        transform: `translateX(${idx * 100}%)`,
        background: "var(--surface)", borderRadius: "var(--radius-pill)", boxShadow: "var(--shadow-xs)",
        transition: "transform var(--dur-base) var(--ease-out)",
        pointerEvents: "none", zIndex: 0,
      }} />
      {options.map((o) => {
        const on = o.value === val;
        return (
          <button
            key={o.value}
            onClick={() => set(o.value)}
            style={{
              position: "relative", zIndex: 1, display: "inline-flex", alignItems: "center", justifyContent: "center", gap: 7,
              height: "100%", padding: "0 18px", border: "none", background: "transparent", cursor: "pointer",
              fontFamily: "var(--font-text)", fontSize: "var(--text-sm)", fontWeight: "var(--weight-semibold)",
              color: on ? "var(--text)" : "var(--text-subtle)", transition: "color var(--dur-fast) var(--ease-out)",
              whiteSpace: "nowrap",
            }}
          >
            {o.icon && <span aria-hidden="true" style={{
              width: 16, height: 16, backgroundColor: "currentColor",
              WebkitMaskImage: `url(https://unpkg.com/lucide-static@0.456.0/icons/${o.icon}.svg)`, maskImage: `url(https://unpkg.com/lucide-static@0.456.0/icons/${o.icon}.svg)`,
              WebkitMaskRepeat: "no-repeat", maskRepeat: "no-repeat", WebkitMaskPosition: "center", maskPosition: "center", WebkitMaskSize: "contain", maskSize: "contain",
            }} />}
            {o.label}
          </button>
        );
      })}
    </div>
  );
}
