import React from "react";
import { iconMask } from "../core/iconMask.js";

/**
 * Free Joy — FileUpload
 * Drag-and-drop file dropzone with click-to-browse and a selected-file list.
 * Uncontrolled list state; reports selections via onFiles.
 */
export function FileUpload({
  accept,
  multiple = true,
  hint = "PDF, PNG, or JPG up to 10MB",
  onFiles,
  style,
  ...rest
}) {
  const [over, setOver] = React.useState(false);
  const [files, setFiles] = React.useState([]);
  const inputRef = React.useRef(null);

  const add = (list) => {
    const arr = Array.from(list);
    const next = multiple ? [...files, ...arr] : arr.slice(0, 1);
    setFiles(next);
    onFiles && onFiles(next);
  };
  const remove = (idx) => {
    const next = files.filter((_, i) => i !== idx);
    setFiles(next);
    onFiles && onFiles(next);
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 12, ...style }} {...rest}>
      <div
        role="button"
        tabIndex={0}
        onClick={() => inputRef.current && inputRef.current.click()}
        onDragOver={(e) => { e.preventDefault(); setOver(true); }}
        onDragLeave={() => setOver(false)}
        onDrop={(e) => { e.preventDefault(); setOver(false); add(e.dataTransfer.files); }}
        style={{
          display: "flex", flexDirection: "column", alignItems: "center", gap: 8,
          padding: "32px 24px", textAlign: "center", cursor: "pointer",
          borderRadius: "var(--radius-lg)",
          border: `1.5px dashed ${over ? "var(--accent)" : "var(--border-strong)"}`,
          background: over ? "var(--accent-soft)" : "var(--surface-2)",
          transition: "all var(--dur-fast) var(--ease-out)",
        }}
      >
        <span aria-hidden="true" style={{
          width: 26, height: 26, backgroundColor: over ? "var(--accent)" : "var(--text-subtle)",
          WebkitMaskImage: iconMask("upload-cloud"),
          maskImage: iconMask("upload-cloud"),
          WebkitMaskRepeat: "no-repeat", maskRepeat: "no-repeat", WebkitMaskPosition: "center", maskPosition: "center", WebkitMaskSize: "contain", maskSize: "contain",
        }} />
        <span style={{ fontFamily: "var(--font-text)", fontSize: "var(--text-base)", color: "var(--text)" }}>
          <strong style={{ color: "var(--accent)" }}>Click to upload</strong> or drag and drop
        </span>
        <span style={{ fontSize: "var(--text-xs)", color: "var(--text-subtle)" }}>{hint}</span>
        <input ref={inputRef} type="file" accept={accept} multiple={multiple} onChange={(e) => add(e.target.files)} style={{ display: "none" }} />
      </div>
      {files.length > 0 && (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {files.map((f, i) => (
            <div key={i} style={{
              display: "flex", alignItems: "center", gap: 10, padding: "10px 12px",
              background: "var(--surface)", border: "1px solid var(--border)", borderRadius: "var(--radius-md)",
            }}>
              <span aria-hidden="true" style={{
                width: 18, height: 18, flex: "none", backgroundColor: "var(--text-subtle)",
                WebkitMaskImage: iconMask("file"),
                maskImage: iconMask("file"),
                WebkitMaskRepeat: "no-repeat", maskRepeat: "no-repeat", WebkitMaskPosition: "center", maskPosition: "center", WebkitMaskSize: "contain", maskSize: "contain",
              }} />
              <span style={{ flex: 1, fontSize: "var(--text-sm)", color: "var(--text)", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{f.name}</span>
              <span style={{ fontSize: "var(--text-xs)", color: "var(--text-subtle)", fontFamily: "var(--font-mono)" }}>{(f.size / 1024).toFixed(0)} KB</span>
              <button type="button" onClick={() => remove(i)} aria-label="Remove"
                style={{ flex: "none", width: 22, height: 22, border: "none", background: "transparent", cursor: "pointer", color: "var(--text-subtle)", fontSize: 16 }}>×</button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
