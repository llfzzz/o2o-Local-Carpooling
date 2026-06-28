import React from "react";

/**
 * Free Joy — Input
 * Text field with optional label, hint, error, and leading icon.
 */
export function Input({
  label,
  hint,
  error,
  iconLeft,
  size = "md",
  id,
  style,
  ...rest
}) {
  const [focus, setFocus] = React.useState(false);
  const fid = id || React.useId();
  const h = { sm: 36, md: 44, lg: 52 }[size] || 44;
  const borderColor = error ? "var(--danger-500)" : focus ? "var(--accent)" : "var(--border-strong)";
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 6, ...style }}>
      {label && (
        <label htmlFor={fid} style={{ fontSize: "var(--text-sm)", fontWeight: "var(--weight-medium)", color: "var(--text-muted)" }}>
          {label}
        </label>
      )}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 8,
          height: h,
          padding: "0 14px",
          background: "var(--surface)",
          border: `1px solid ${borderColor}`,
          borderRadius: "var(--radius-md)",
          boxShadow: focus ? "var(--ring)" : "none",
          transition: "border-color var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast) var(--ease-out)",
        }}
      >
        {iconLeft && <span style={{ color: "var(--text-subtle)", display: "inline-flex" }}>{iconLeft}</span>}
        <input
          id={fid}
          onFocus={() => setFocus(true)}
          onBlur={() => setFocus(false)}
          style={{
            flex: 1,
            border: "none",
            outline: "none",
            background: "transparent",
            fontFamily: "var(--font-text)",
            fontSize: "var(--text-base)",
            color: "var(--text)",
            minWidth: 0,
          }}
          {...rest}
        />
      </div>
      {(hint || error) && (
        <span style={{ fontSize: "var(--text-xs)", color: error ? "var(--danger-700)" : "var(--text-subtle)" }}>
          {error || hint}
        </span>
      )}
    </div>
  );
}
