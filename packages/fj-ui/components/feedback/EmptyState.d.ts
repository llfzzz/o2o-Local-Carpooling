import * as React from "react";

/** Centered placeholder for empty / no-results screens. */
export interface EmptyStateProps extends React.HTMLAttributes<HTMLDivElement> {
  /** Lucide icon name. @default "inbox" */
  icon?: string;
  title?: React.ReactNode;
  description?: React.ReactNode;
  /** Optional CTA node (e.g. a Button). */
  action?: React.ReactNode;
  /** Tighter padding. @default false */
  compact?: boolean;
}
export declare function EmptyState(props: EmptyStateProps): JSX.Element;
