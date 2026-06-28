import * as React from "react";

/** Single KPI card: label, value, delta, and trend. */
export interface StatProps extends React.HTMLAttributes<HTMLDivElement> {
  label?: React.ReactNode;
  value?: React.ReactNode;
  /** Change figure, e.g. "+12.4%". */
  delta?: React.ReactNode;
  /** Tints + arrow for the delta. */
  trend?: "up" | "down" | "flat";
  sublabel?: React.ReactNode;
  /** Lucide icon name (top-right). */
  icon?: string;
}
export declare function Stat(props: StatProps): JSX.Element;
