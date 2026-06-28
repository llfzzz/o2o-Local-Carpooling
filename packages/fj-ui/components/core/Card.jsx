import React from "react";

/**
 * Free Joy — Card
 * Editorial surface: white, hairline border, generous padding, soft shadow on hover (optional).
 * Set glass to render a frosted iOS-18-style translucent panel instead of solid white.
 */
export function Card({ interactive = false, glass = false, padding = "var(--space-5)", style, children, ...rest }) {
  const [hover, setHover] = React.useState(false);
  const glassStyle = glass
    ? {
        background: "var(--glass-bg)",
        WebkitBackdropFilter: "blur(var(--glass-blur)) saturate(160%)",
        backdropFilter: "blur(var(--glass-blur)) saturate(160%)",
        border: "1px solid var(--glass-border)",
      }
    : {
        background: "var(--surface)",
        border: "1px solid var(--border)",
      };
  return (
    <div
      onMouseEnter={() => interactive && setHover(true)}
      onMouseLeave={() => interactive && setHover(false)}
      style={{
        ...glassStyle,
        borderRadius: "var(--radius-lg)",
        padding,
        boxShadow: hover ? "var(--shadow-md)" : "var(--shadow-xs)",
        transform: hover ? "translateY(-2px)" : "translateY(0)",
        transition: "box-shadow var(--dur-base) var(--ease-out), transform var(--dur-base) var(--ease-out)",
        cursor: interactive ? "pointer" : "default",
        ...style,
      }}
      {...rest}
    >
      {children}
    </div>
  );
}
