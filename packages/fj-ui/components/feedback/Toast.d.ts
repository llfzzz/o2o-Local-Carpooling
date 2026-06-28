import * as React from "react";

export interface ToastOptions {
  title?: React.ReactNode;
  description?: React.ReactNode;
  tone?: "neutral" | "success" | "warn" | "danger" | "info";
  /** Auto-dismiss ms (0 = sticky). @default 4000 */
  duration?: number;
  /** Lucide icon name override. */
  icon?: string;
}

export interface ToastProviderProps {
  /** @default "bottom-right" */
  position?: "bottom-right" | "bottom-left" | "top-right" | "top-left";
  children?: React.ReactNode;
}
export declare function ToastProvider(props: ToastProviderProps): JSX.Element;

/** Returns a `push(options | title)` function. Must be inside <ToastProvider>. */
export declare function useToast(): (opts: ToastOptions | string) => string;

export interface ToastProps extends ToastOptions {
  onClose?: () => void;
}
export declare function Toast(props: ToastProps): JSX.Element;
