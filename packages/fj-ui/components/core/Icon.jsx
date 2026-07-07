import React from "react";
import { iconMask } from "./iconMask.js";

/**
 * Free Joy — Icon
 * Renders a Lucide-style icon via CSS mask (inherits currentColor).
 */
export function Icon({ name, size = 20, color = "currentColor", strokeWidth, style, ...rest }) {
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
        WebkitMaskImage: iconMask(name),
        maskImage: iconMask(name),
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
