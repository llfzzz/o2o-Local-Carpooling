import React from "react";

/**
 * Free Joy — List
 * Vertical list of rows with optional leading media, title/subtitle, and trailing slot.
 * items: [{ id, icon, avatar, title, subtitle, trailing, onClick }]
 */
export function List({ items = [], divided = true, style }) {
  return (
    <div role="list" style={{ display: "flex", flexDirection: "column", background: "var(--surface)", border: "1px solid var(--border)", borderRadius: "var(--radius-lg)", overflow: "hidden", ...style }}>
      {items.map((it, i) => {
        const clickable = !!it.onClick;
        return (
          <div
            key={it.id ?? i}
            role="listitem"
            onClick={it.onClick}
            style={{
              display: "flex", alignItems: "center", gap: 14, padding: "14px 18px",
              borderTop: divided && i ? "1px solid var(--border)" : "none",
              cursor: clickable ? "pointer" : "default", transition: "background var(--dur-fast) var(--ease-out)",
            }}
            onMouseEnter={(e) => clickable && (e.currentTarget.style.background = "var(--surface-hover)")}
            onMouseLeave={(e) => clickable && (e.currentTarget.style.background = "transparent")}
          >
            {it.avatar && <img src={it.avatar} alt="" style={{ width: 40, height: 40, borderRadius: "50%", objectFit: "cover", flex: "none" }} />}
            {it.icon && !it.avatar && (
              <span style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", width: 40, height: 40, borderRadius: "var(--radius-md)", background: "var(--paper-2)", flex: "none" }}>
                <span aria-hidden="true" style={{
                  width: 20, height: 20, backgroundColor: "var(--text-muted)",
                  WebkitMaskImage: `url(https://unpkg.com/lucide-static@0.456.0/icons/${it.icon}.svg)`, maskImage: `url(https://unpkg.com/lucide-static@0.456.0/icons/${it.icon}.svg)`,
                  WebkitMaskRepeat: "no-repeat", maskRepeat: "no-repeat", WebkitMaskPosition: "center", maskPosition: "center", WebkitMaskSize: "contain", maskSize: "contain",
                }} />
              </span>
            )}
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontFamily: "var(--font-text)", fontSize: "var(--text-base)", fontWeight: "var(--weight-semibold)", color: "var(--text)", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{it.title}</div>
              {it.subtitle && <div style={{ fontSize: "var(--text-sm)", color: "var(--text-muted)", marginTop: 2, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{it.subtitle}</div>}
            </div>
            {it.trailing && <div style={{ flex: "none", color: "var(--text-subtle)" }}>{it.trailing}</div>}
          </div>
        );
      })}
    </div>
  );
}
