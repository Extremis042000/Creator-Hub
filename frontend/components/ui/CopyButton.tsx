"use client";

import { useState } from "react";
import { Button } from "./Button";

export function CopyButton({ value, label = "Copy" }: { value: string; label?: string }) {
  const [copied, setCopied] = useState(false);

  async function handleClick() {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
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
