import * as React from "react";

/**
 * Editorial content surface — white, hairline border, generous padding.
 *
 * @startingPoint section="Core" subtitle="Hairline card surface" viewport="700x240"
 */
export interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  /** Lift + shadow on hover, pointer cursor. @default false */
  interactive?: boolean;
  /** Render a frosted iOS-18-style translucent panel instead of solid white. @default false */
  glass?: boolean;
  /** CSS padding value. @default var(--space-5) */
  padding?: string;
  children?: React.ReactNode;
}
export declare function Card(props: CardProps): JSX.Element;
