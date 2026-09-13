import type { PerformanceCategory } from "@/lib/api";

const STYLES: Record<PerformanceCategory, string> = {
  DEVELOPING: "text-status-low border-status-low",
  SOLID: "text-status-medium border-status-medium",
  STRONG: "text-status-high border-status-high",
  ELITE: "text-brand-accent border-brand-accent",
  UNDEFEATED: "text-brand-accent border-brand-accent",
  NO_DATA: "text-status-none border-status-none",
};

const LABELS: Record<PerformanceCategory, string> = {
  DEVELOPING: "Developing",
  SOLID: "Solid",
  STRONG: "Strong",
  ELITE: "Elite",
  UNDEFEATED: "Undefeated",
  NO_DATA: "No data",
};

export function CategoryBadge({ category }: { category: PerformanceCategory }) {
  return (
    <span
      className={`inline-flex items-center rounded-full border px-3 py-1 text-sm font-semibold ${STYLES[category]}`}
    >
      {LABELS[category]}
    </span>
  );
}
