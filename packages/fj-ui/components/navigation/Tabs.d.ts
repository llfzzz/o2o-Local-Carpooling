import * as React from "react";

export interface TabItem { id: string; label: string; }

/** Pill-style segmented tab control. Controlled via value + onChange. */
export interface TabsProps {
  items: TabItem[];
  /** Active tab id. Defaults to the first item. */
  value?: string;
  onChange?: (id: string) => void;
}
export declare function Tabs(props: TabsProps): JSX.Element;
