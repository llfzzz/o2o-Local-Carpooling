import React from "react";
import { iconMask } from "../core/iconMask.js";

const ACC = { coral: "var(--joy-500)", sun: "var(--sun-500)", bloom: "var(--bloom-500)" };

/**
 * Free Joy — Timeline
 * Vertical activity timeline. items: [{ title, time, body, icon, accent }]
 */
export function Timeline({ items = [], style }) {
  return (
    <div style={{ display: "flex", flexDirection: "column", ...style }}>
      {items.map((it, i) => {
        const color = ACC[it.accent] || it.accent || "var(--accent)";
        const last = i === items.length - 1;
        return (
          <div key={i} style={{ display: "flex", gap: 16 }}>
            <div style={{ display: "flex", flexDirection: "column", alignItems: "center", flex: "none" }}>
              <span style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", width: 32, height: 32, borderRadius: "50%", background: it.icon ? "var(--surface)" : color, border: it.icon ? `2px solid ${color}` : "none", flex: "none" }}>
                {it.icon && <span aria-hidden="true" style={{
                  width: 15, height: 15, backgroundColor: color,
                  WebkitMaskImage: iconMask(it.icon), maskImage: iconMask(it.icon),
                  WebkitMaskRepeat: "no-repeat", maskRepeat: "no-repeat", WebkitMaskPosition: "center", maskPosition: "center", WebkitMaskSize: "contain", maskSize: "contain",
                }} />}
              </span>
              {!last && <span style={{ width: 2, flex: 1, minHeight: 24, background: "var(--border)", marginTop: 4 }} />}
            </div>
            <div style={{ paddingBottom: last ? 0 : 26, flex: 1 }}>
              <div style={{ display: "flex", alignItems: "baseline", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
                <span style={{ fontFamily: "var(--font-text)", fontSize: "var(--text-base)", fontWeight: "var(--weight-semibold)", color: "var(--text)" }}>{it.title}</span>
                {it.time && <span style={{ fontFamily: "var(--font-mono)", fontSize: "var(--text-xs)", color: "var(--text-subtle)" }}>{it.time}</span>}
              </div>
              {it.body && <div style={{ fontSize: "var(--text-sm)", color: "var(--text-muted)", marginTop: 4, lineHeight: 1.55 }}>{it.body}</div>}
            </div>
          </div>
        );
      })}
    </div>
  );
}
