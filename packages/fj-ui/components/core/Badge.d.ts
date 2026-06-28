import * as React from "react";

/** Small status / count pill. */
export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  /** Color tone. @default "neutral" */
  tone?: "neutral" | "accent" | "success" | "warn" | "danger" | "sun" | "bloom";
  /** Filled (solid) vs soft tinted. @default false */
  solid?: boolean;
  children?: React.ReactNode;
}
export declare function Badge(props: BadgeProps): JSX.Element;
