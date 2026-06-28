import React from "react";

/**
 * Free Joy — Button
 * Variants: primary | secondary | ghost | danger
 * Sizes: sm | md | lg
 */
export function Button({
  variant = "primary",
  size = "md",
  iconLeft,
  iconRight,
  disabled = false,
  full = false,
  style,
  children,
  ...rest
}) {
  const sizes = {
    sm: { padding: "0 14px", height: 36, fontSize: "var(--text-sm)", gap: 6 },
    md: { padding: "0 20px", height: 44, fontSize: "var(--text-base)", gap: 8 },
    lg: { padding: "0 28px", height: 54, fontSize: "var(--text-md)", gap: 10 },
  };
  const variants = {
    primary: {
      background: "var(--accent)",
      color: "var(--text-on-accent)",
      border: "1px solid transparent",
    },
    secondary: {
      background: "var(--surface)",
      color: "var(--text)",
      border: "1px solid var(--border-strong)",
    },
    ghost: {
      background: "transparent",
      color: "var(--text)",
      border: "1px solid transparent",
    },
    danger: {
      background: "var(--danger-500)",
      color: "var(--white)",
      border: "1px solid transparent",
    },
  };
  const s = sizes[size] || sizes.md;
  const v = variants[variant] || variants.primary;

  const [hover, setHover] = React.useState(false);
  const [active, setActive] = React.useState(false);

  const hoverBg = {
    primary: "var(--accent-hover)",
    secondary: "var(--surface-hover)",
    ghost: "var(--surface-hover)",
    danger: "var(--danger-700)",
  }[variant];

  return (
    <button
      disabled={disabled}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => { setHover(false); setActive(false); }}
      onMouseDown={() => setActive(true)}
      onMouseUp={() => setActive(false)}
      style={{
        display: full ? "flex" : "inline-flex",
        width: full ? "100%" : undefined,
        alignItems: "center",
        justifyContent: "center",
        gap: s.gap,
        height: s.height,
        padding: s.padding,
        fontFamily: "var(--font-text)",
        fontWeight: "var(--weight-semibold)",
        fontSize: s.fontSize,
        lineHeight: 1,
        borderRadius: "var(--radius-pill)",
        cursor: disabled ? "not-allowed" : "pointer",
        opacity: disabled ? 0.45 : 1,
        transform: active && !disabled ? "scale(0.97)" : "scale(1)",
        transition: "background var(--dur-fast) var(--ease-out), transform var(--dur-fast) var(--ease-out)",
        whiteSpace: "nowrap",
        ...v,
        background: hover && !disabled ? hoverBg : v.background,
        ...style,
      }}
      {...rest}
    >
      {iconLeft}
      {children}
      {iconRight}
    </button>
  );
}
