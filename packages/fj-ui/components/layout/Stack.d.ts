import * as React from "react";

/** Flexbox layout primitive — row or column with a gap. */
export interface StackProps extends React.HTMLAttributes<HTMLDivElement> {
  /** @default "column" */
  direction?: "row" | "column" | "row-reverse" | "column-reverse";
  /** Gap between children, px. @default 16 */
  gap?: number;
  align?: React.CSSProperties["alignItems"];
  justify?: React.CSSProperties["justifyContent"];
  /** @default false */
  wrap?: boolean;
  /** Render as inline-flex. @default false */
  inline?: boolean;
  children?: React.ReactNode;
}
export declare function Stack(props: StackProps): JSX.Element;
