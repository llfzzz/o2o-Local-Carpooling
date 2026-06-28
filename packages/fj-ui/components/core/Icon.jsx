import React from "react";

/**
 * Free Joy — Icon
 * Renders a Lucide icon via CSS mask (inherits currentColor).
 * Icon set: Lucide (https://lucide.dev) — loaded from CDN as needed.
 */
export function Icon({ name, size = 20, color = "currentColor", strokeWidth, style, ...rest }) {
  const url = `https://unpkg.com/lucide-static@0.456.0/icons/${name}.svg`;
  return (
    <span
      role="img"
      aria-label={name}
      style={{
        display: "inline-block",
        width: size,
        height: size,
        flex: "none",
        backgroundColor: color,
        WebkitMaskImage: `url(${url})`,
        maskImage: `url(${url})`,
        WebkitMaskRepeat: "no-repeat",
        maskRepeat: "no-repeat",
        WebkitMaskPosition: "center",
        maskPosition: "center",
        WebkitMaskSize: "contain",
        maskSize: "contain",
        ...style,
      }}
      {...rest}
    />
  );
}
