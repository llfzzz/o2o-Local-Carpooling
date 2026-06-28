import React from "react";

/**
 * Free Joy — Stack
 * Flexbox layout primitive. Lays children in a row or column with a gap.
 */
export function Stack({
  direction = "column",
  gap = 16,
  align,
  justify,
  wrap = false,
  inline = false,
  style,
  children,
  ...rest
}) {
  return (
    <div
      style={{
        display: inline ? "inline-flex" : "flex",
        flexDirection: direction,
        gap,
        alignItems: align,
        justifyContent: justify,
        flexWrap: wrap ? "wrap" : "nowrap",
        ...style,
      }}
      {...rest}
    >
      {children}
    </div>
  );
}
