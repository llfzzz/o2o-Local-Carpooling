import * as React from "react";

/** Drag-and-drop file dropzone with a selected-file list. */
export interface FileUploadProps {
  /** HTML accept attribute, e.g. "image/*,.pdf". */
  accept?: string;
  /** @default true */
  multiple?: boolean;
  /** Helper text under the prompt. */
  hint?: React.ReactNode;
  /** Fires with the current File[] on every change. */
  onFiles?: (files: File[]) => void;
  style?: React.CSSProperties;
}
export declare function FileUpload(props: FileUploadProps): JSX.Element;
