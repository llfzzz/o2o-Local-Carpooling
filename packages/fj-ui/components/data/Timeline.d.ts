import * as React from "react";

export interface TimelineItem {
  title: React.ReactNode;
  time?: React.ReactNode;
  body?: React.ReactNode;
  /** Lucide icon name; omit for a solid dot. */
  icon?: string;
  /** Node color. */
  accent?: "coral" | "sun" | "bloom" | string;
}

/** Vertical activity timeline. */
export interface TimelineProps {
  items: TimelineItem[];
  style?: React.CSSProperties;
}
export declare function Timeline(props: TimelineProps): JSX.Element;
