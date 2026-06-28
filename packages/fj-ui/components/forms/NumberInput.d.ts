import * as React from "react";

/** Numeric input with −/+ stepper buttons and clamping. */
export interface NumberInputProps {
  value?: number;
  /** @default 0 */
  defaultValue?: number;
  min?: number;
  max?: number;
  /** @default 1 */
  step?: number;
  onChange?: (value: number) => void;
  label?: React.ReactNode;
  hint?: React.ReactNode;
  /** @default "md" */
  size?: "sm" | "md" | "lg";
  disabled?: boolean;
  style?: React.CSSProperties;
}
export declare function NumberInput(props: NumberInputProps): JSX.Element;
