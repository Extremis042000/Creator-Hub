"use client";

import { useState } from "react";
import { Button } from "./Button";

export function CopyButton({
  value,
  label = "Copy",
  onCopy,
}: {
  value: string;
  label?: string;
  /** Phase 37: fires only after a real successful clipboard write -- e.g. reporting a first-party usage signal. Optional; most callers don't need it. */
  onCopy?: () => void;
}) {
  const [copied, setCopied] = useState(false);

  async function handleClick() {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      onCopy?.();
      setTimeout(() => setCopied(false), 1500);
    } catch {
      // Clipboard API can be unavailable (permissions, non-secure
      // context); fail silently rather than throwing in the UI.
    }
  }

  return (
    <Button type="button" variant="secondary" onClick={handleClick}>
      {copied ? "Copied!" : label}
    </Button>
  );
}
