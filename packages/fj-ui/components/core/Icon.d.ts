import * as React from "react";

/**
 * Lucide icon rendered via CSS mask so it inherits the surrounding text color.
 */
export interface IconProps {
  /** Lucide icon name, e.g. "arrow-right", "heart", "search". */
  name: string;
  /** Pixel size (square). @default 20 */
  size?: number;
  /** Override color. @default "currentColor" */
  color?: string;
  style?: React.CSSProperties;
}
export declare function Icon(props: IconProps): JSX.Element;
