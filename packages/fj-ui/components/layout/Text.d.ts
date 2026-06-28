import * as React from "react";

/** Editorial typography — the whole type scale in one component. */
export interface TextProps extends React.HTMLAttributes<HTMLElement> {
  /** Type role. @default "body" */
  variant?: "display" | "h1" | "h2" | "h3" | "h4" | "lead" | "body" | "small" | "eyebrow" | "mono";
  /** Override the rendered element. */
  as?: keyof JSX.IntrinsicElements;
  color?: string;
  align?: "left" | "center" | "right";
  weight?: number;
  /** Single-line ellipsis truncation. @default false */
  truncate?: boolean;
  children?: React.ReactNode;
}
export declare function Text(props: TextProps): JSX.Element;
