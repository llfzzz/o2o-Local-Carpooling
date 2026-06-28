import * as React from "react";

export interface SegmentOption {
  value: string;
  label: React.ReactNode;
  /** Lucide icon name. */
  icon?: string;
}

/** iOS-style segmented control with a sliding thumb. */
export interface SegmentedControlProps {
  options: SegmentOption[];
  value?: string;
  defaultValue?: string;
  onChange?: (value: string) => void;
  /** @default "md" */
  size?: "sm" | "md" | "lg";
  /** Stretch to fill width. @default false */
  full?: boolean;
  style?: React.CSSProperties;
}
export declare function SegmentedControl(props: SegmentedControlProps): JSX.Element;
