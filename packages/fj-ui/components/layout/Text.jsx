import React from "react";

/**
 * Free Joy — Text (Typography)
 * One component for the whole editorial type scale. Picks a sensible element
 * per variant; override with `as`.
 */
export function Text({
  variant = "body",
  as,
  color,
  align,
  weight,
  truncate = false,
  style,
  children,
  ...rest
}) {
  const variants = {
    display: { fontFamily: "var(--font-display)", fontSize: "var(--text-5xl)", fontWeight: "var(--weight-bold)", lineHeight: 1.05, letterSpacing: "var(--tracking-tight)" },
    h1:      { fontFamily: "var(--font-display)", fontSize: "var(--text-4xl)", fontWeight: "var(--weight-bold)", lineHeight: 1.1, letterSpacing: "var(--tracking-tight)" },
    h2:      { fontFamily: "var(--font-display)", fontSize: "var(--text-3xl)", fontWeight: "var(--weight-semibold)", lineHeight: 1.12, letterSpacing: "var(--tracking-snug)" },
    h3:      { fontFamily: "var(--font-display)", fontSize: "var(--text-2xl)", fontWeight: "var(--weight-semibold)", lineHeight: 1.18 },
    h4:      { fontFamily: "var(--font-text)", fontSize: "var(--text-xl)", fontWeight: "var(--weight-semibold)", lineHeight: 1.3 },
    lead:    { fontFamily: "var(--font-text)", fontSize: "var(--text-md)", fontWeight: "var(--weight-regular)", lineHeight: 1.55, color: "var(--text-muted)" },
    body:    { fontFamily: "var(--font-text)", fontSize: "var(--text-base)", fontWeight: "var(--weight-regular)", lineHeight: 1.6 },
    small:   { fontFamily: "var(--font-text)", fontSize: "var(--text-sm)", lineHeight: 1.5, color: "var(--text-muted)" },
    eyebrow: { fontFamily: "var(--font-mono)", fontSize: "var(--text-xs)", fontWeight: "var(--weight-medium)", letterSpacing: "var(--tracking-caps)", textTransform: "uppercase", color: "var(--text-subtle)" },
    mono:    { fontFamily: "var(--font-mono)", fontSize: "var(--text-sm)", lineHeight: 1.5 },
  };
  const tagMap = { display: "h1", h1: "h1", h2: "h2", h3: "h3", h4: "h4", lead: "p", body: "p", small: "p", eyebrow: "span", mono: "span" };
  const Tag = as || tagMap[variant] || "p";
  const v = variants[variant] || variants.body;
  const trunc = truncate ? { overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" } : {};
  return (
    <Tag
      style={{
        margin: 0,
        color: "var(--text)",
        textWrap: "pretty",
        ...v,
        ...(color ? { color } : {}),
        ...(align ? { textAlign: align } : {}),
        ...(weight ? { fontWeight: weight } : {}),
        ...trunc,
        ...style,
      }}
      {...rest}
    >
      {children}
    </Tag>
  );
}
