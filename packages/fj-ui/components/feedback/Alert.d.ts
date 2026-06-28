import * as React from "react";

/**
 * Inline message banner with a tone, optional title, icon and dismiss.
 *
 * @startingPoint section="Feedback" subtitle="Inline status banner" viewport="700x160"
 */
export interface AlertProps extends React.HTMLAttributes<HTMLDivElement> {
  /** @default "info" */
  tone?: "info" | "success" | "warn" | "danger" | "neutral";
  title?: string;
  icon?: React.ReactNode;
  /** Show a dismiss button that calls this. */
  onClose?: () => void;
}
export declare function Alert(props: AlertProps): JSX.Element;
