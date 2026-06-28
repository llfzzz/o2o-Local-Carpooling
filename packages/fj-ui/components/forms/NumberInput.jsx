import React from "react";

/**
 * Free Joy — NumberInput
 * Numeric field with stepper buttons, min/max clamping, and step.
 */
export function NumberInput({
  value,
  defaultValue = 0,
  min = -Infinity,
  max = Infinity,
  step = 1,
  onChange,
  label,
  hint,
  size = "md",
  disabled = false,
  style,
  ...rest
}) {
  const [internal, setInternal] = React.useState(defaultValue);
  const [focus, setFocus] = React.useState(false);
  const fid = React.useId();
  const val = value ?? internal;
  const h = { sm: 36, md: 44, lg: 52 }[size] || 44;

  const set = (v) => {
    const c = Math.min(max, Math.max(min, v));
    if (value === undefined) setInternal(c);
    onChange && onChange(c);
  };

  const StepBtn = ({ dir, glyph }) => (
    <button
      type="button"
      disabled={disabled || (dir > 0 ? val >= max : val <= min)}
      onClick={() => set(Number(val) + dir * step)}
      style={{
        width: h - 8, height: "100%", border: "none", background: "transparent",
        cursor: "pointer", color: "var(--text-muted)", fontSize: 18, lineHeight: 1,
        display: "flex", alignItems: "center", justifyContent: "center",
      }}
    >
      {glyph}
    </button>
  );

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 6, ...style }}>
      {label && <label htmlFor={fid} style={{ fontSize: "var(--text-sm)", fontWeight: "var(--weight-medium)", color: "var(--text-muted)" }}>{label}</label>}
      <div style={{
        display: "flex", alignItems: "center", height: h,
        background: "var(--surface)",
        border: `1px solid ${focus ? "var(--accent)" : "var(--border-strong)"}`,
        borderRadius: "var(--radius-md)",
        boxShadow: focus ? "var(--ring)" : "none",
        opacity: disabled ? 0.55 : 1,
        transition: "border-color var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast) var(--ease-out)",
      }}>
        <StepBtn dir={-1} glyph="−" />
        <input
          id={fid}
          type="number"
          value={val}
          disabled={disabled}
          onFocus={() => setFocus(true)}
          onBlur={() => setFocus(false)}
          onChange={(e) => set(e.target.value === "" ? 0 : Number(e.target.value))}
          style={{
            flex: 1, width: "100%", minWidth: 0, border: "none", outline: "none",
            background: "transparent", textAlign: "center", fontFamily: "var(--font-text)",
            fontSize: "var(--text-base)", fontWeight: "var(--weight-medium)", color: "var(--text)",
            MozAppearance: "textfield",
          }}
          {...rest}
        />
        <StepBtn dir={1} glyph="+" />
      </div>
      {hint && <span style={{ fontSize: "var(--text-xs)", color: "var(--text-subtle)" }}>{hint}</span>}
    </div>
  );
}
