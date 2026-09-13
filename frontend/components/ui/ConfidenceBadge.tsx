export type Confidence = "HIGH" | "MEDIUM" | "LOW" | "INSUFFICIENT_DATA";

const STYLES: Record<Confidence, string> = {
  HIGH: "text-status-high border-status-high",
  MEDIUM: "text-status-medium border-status-medium",
  LOW: "text-status-low border-status-low",
  INSUFFICIENT_DATA: "text-status-none border-status-none",
};

const LABELS: Record<Confidence, string> = {
  HIGH: "High confidence",
  MEDIUM: "Medium confidence",
  LOW: "Low confidence",
  INSUFFICIENT_DATA: "Not enough data yet",
};

/**
 * Confidence is never conveyed by color alone — always paired with
 * the word. See docs/09-phase3-ui-ux.md §11 (Accessibility).
 */
export function ConfidenceBadge({ confidence }: { confidence: Confidence }) {
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium ${STYLES[confidence]}`}
    >
      {LABELS[confidence]}
    </span>
  );
}
