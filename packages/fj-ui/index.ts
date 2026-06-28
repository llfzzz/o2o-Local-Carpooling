/**
 * Free Joy (FJ) Design System — local barrel.
 *
 * Imported into this repo from the "Free Joy Design System" Claude Design
 * project (id a42e56e8-d278-47e6-9679-38bc1fc507bb) and re-themed to the
 * carpooling teal accent via tokens/brand-carpool.css. See README.md.
 *
 * This barrel re-exports the curated component subset used by the two apps.
 * Pull additional components (overlay/data-grid/etc.) on demand via the same
 * DesignSync flow when needed.
 */

// core
export { Button } from "./components/core/Button";
export type { ButtonProps } from "./components/core/Button";
export { Card } from "./components/core/Card";
export type { CardProps } from "./components/core/Card";
export { Icon } from "./components/core/Icon";
export type { IconProps } from "./components/core/Icon";
export { Tag } from "./components/core/Tag";
export type { TagProps } from "./components/core/Tag";
export { Badge } from "./components/core/Badge";
export type { BadgeProps } from "./components/core/Badge";

// layout
export { Stack } from "./components/layout/Stack";
export type { StackProps } from "./components/layout/Stack";
export { Text } from "./components/layout/Text";
export type { TextProps } from "./components/layout/Text";

// forms
export { Input } from "./components/forms/Input";
export type { InputProps } from "./components/forms/Input";
export { NumberInput } from "./components/forms/NumberInput";
export type { NumberInputProps } from "./components/forms/NumberInput";
export { FileUpload } from "./components/forms/FileUpload";
export type { FileUploadProps } from "./components/forms/FileUpload";

// navigation
export { Tabs } from "./components/navigation/Tabs";
export type { TabsProps, TabItem } from "./components/navigation/Tabs";
export { SegmentedControl } from "./components/navigation/SegmentedControl";
export type { SegmentedControlProps, SegmentOption } from "./components/navigation/SegmentedControl";

// feedback
export { Alert } from "./components/feedback/Alert";
export type { AlertProps } from "./components/feedback/Alert";
export { Toast, ToastProvider, useToast } from "./components/feedback/Toast";
export type { ToastProps, ToastOptions, ToastProviderProps } from "./components/feedback/Toast";
export { EmptyState } from "./components/feedback/EmptyState";
export type { EmptyStateProps } from "./components/feedback/EmptyState";

// data
export { List } from "./components/data/List";
export type { ListProps, ListItem } from "./components/data/List";
export { Stat } from "./components/data/Stat";
export type { StatProps } from "./components/data/Stat";
export { Timeline } from "./components/data/Timeline";
export type { TimelineProps, TimelineItem } from "./components/data/Timeline";
