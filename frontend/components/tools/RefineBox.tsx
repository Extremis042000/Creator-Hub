"use client";

import { useState, type KeyboardEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { ApiError } from "@/lib/api";

/**
 * Phase 29: "make it punchier" / "make it shorter" follow-up on an
 * AI-generated result. Only ever rendered when the parent has a real
 * refineSessionId (i.e. AI actually produced the current result) --
 * there's no template fallback to refine.
 */
export default function RefineBox<T>({
  sessionId,
  onRefine,
  onResult,
}: {
  sessionId: string;
  onRefine: (sessionId: string, message: string) => Promise<T>;
  onResult: (result: T) => void;
}) {
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleRefine() {
    const trimmed = message.trim();
    if (!trimmed) return;
    setLoading(true);
    setError(null);
    try {
      const result = await onRefine(sessionId, trimmed);
      onResult(result);
      setMessage("");
    } catch (err) {
      setError(err instanceof ApiError ? err.body.message : "Couldn't refine that result -- please try again.");
    } finally {
      setLoading(false);
    }
  }

  function handleKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Enter") {
      e.preventDefault();
      handleRefine();
    }
  }

  return (
    <div className="mt-2 flex flex-col gap-2 border-t border-border-subtle pt-4">
      <p className="text-xs font-medium text-text-secondary">Want it different? Ask the AI to refine it.</p>
      <div className="flex gap-2">
        <Input
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="e.g. make it punchier, or shorter"
          aria-label="Refine instruction"
        />
        <Button
          type="button"
          onClick={handleRefine}
          disabled={loading || !message.trim()}
          className="whitespace-nowrap text-xs"
        >
          {loading ? "Refining..." : "Refine"}
        </Button>
      </div>
      {error && <p className="text-xs text-brand-primary">{error}</p>}
    </div>
  );
}
