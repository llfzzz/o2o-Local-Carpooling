import * as React from "react";

/** Compact label / chip with optional dot, icon, and remove affordance. */
export interface TagProps extends React.HTMLAttributes<HTMLSpanElement> {
  /** Color: a brand accent name, any CSS color, or "neutral". @default "neutral" */
  accent?: "neutral" | "coral" | "sun" | "bloom" | string;
  /** Show a leading status dot. @default false */
  dot?: boolean;
  /** Leading icon node. */
  icon?: React.ReactNode;
  /** Show a remove (×) button; called when clicked. */
  onRemove?: () => void;
  /** Outline to indicate a selected/active chip. @default false */
  selected?: boolean;
  children?: React.ReactNode;
}
export declare function Tag(props: TagProps): JSX.Element;
