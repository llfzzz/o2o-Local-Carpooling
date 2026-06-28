import * as React from "react";

/**
 * Labeled text input with hint / error states and optional leading icon.
 *
 * @startingPoint section="Forms" subtitle="Text field with label & states" viewport="700x160"
 */
export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  /** Field label rendered above the input. */
  label?: string;
  /** Helper text below the field. */
  hint?: string;
  /** Error message — turns the field red and overrides hint. */
  error?: string;
  /** Icon node rendered inside, before the text. */
  iconLeft?: React.ReactNode;
  /** @default "md" */
  size?: "sm" | "md" | "lg";
}
export declare function Input(props: InputProps): JSX.Element;
