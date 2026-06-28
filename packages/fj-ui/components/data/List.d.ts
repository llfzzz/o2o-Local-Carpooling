import * as React from "react";

export interface ListItem {
  id?: string | number;
  /** Lucide icon name (leading). */
  icon?: string;
  /** Avatar image URL (leading, takes priority over icon). */
  avatar?: string;
  title: React.ReactNode;
  subtitle?: React.ReactNode;
  /** Trailing slot (badge, chevron, button…). */
  trailing?: React.ReactNode;
  onClick?: () => void;
}

/** Vertical row list with media, text, and a trailing slot. */
export interface ListProps {
  items: ListItem[];
  /** Hairline dividers between rows. @default true */
  divided?: boolean;
  style?: React.CSSProperties;
}
export declare function List(props: ListProps): JSX.Element;
