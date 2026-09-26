"use client";

import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { fetchAiSignalSummary, type AiSignalSummary, type ToolSignalBreakdown } from "@/lib/api";

function toolLabel(toolTypeKey: string): string {
  return toolTypeKey
    .split("_")
    .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
    .join(" ");
}

function SegmentedBar({ breakdown }: { breakdown: ToolSignalBreakdown }) {
  const total = breakdown.successfulGenerations || 1; // avoid div-by-zero; a real 0-total tool isn't rendered by the caller anyway
  const segments: { label: string; value: number; className: string }[] = [
    { label: "Copied", value: breakdown.copied, className: "bg-status-high" },
    { label: "Refined", value: breakdown.refined, className: "bg-brand-accent" },
    { label: "Regenerated", value: breakdown.regenerated, className: "bg-brand-primary" },
    { label: "No signal yet", value: breakdown.noSignalYet, className: "bg-border-strong" },
  ];

  return (
    <div>
      <div className="flex h-2 w-full overflow-hidden rounded-full">
        {segments.map((s) =>
          s.value > 0 ? (
            <div key={s.label} className={s.className} style={{ width: `${(s.value / total) * 100}%` }} title={`${s.label}: ${s.value}`} />
          ) : null,
        )}
      </div>
      <ul className="mt-1.5 flex flex-wrap gap-x-3 gap-y-0.5 text-xs text-text-secondary">
        {segments.map((s) => (
          <li key={s.label} className="flex items-center gap-1">
            <span aria-hidden="true" className={`inline-block h-2 w-2 rounded-full ${s.className}`} />
            {s.label}: <span className="text-text-primary">{s.value}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

export default function AdminAiSignalsCard({
  initialSummary,
  token,
}: {
  initialSummary: AiSignalSummary;
  token: string;
}) {
  const [summary, setSummary] = useState(initialSummary);
  const [refreshing, setRefreshing] = useState(false);
  const tools = Object.entries(summary.byTool).sort(([, a], [, b]) => b.successfulGenerations - a.successfulGenerations);

  async function refresh() {
    setRefreshing(true);
    try {
      setSummary(await fetchAiSignalSummary(token));
    } finally {
      setRefreshing(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      {tools.length === 0 ? (
        <p className="text-sm text-text-secondary">
          No successful AI generations yet -- this fills in once premium users start using the AI-enhanced tools.
        </p>
      ) : (
        <div className="flex flex-col gap-4">
          {tools.map(([toolTypeKey, breakdown]) => (
            <div key={toolTypeKey}>
              <div className="flex items-baseline justify-between">
                <p className="text-sm font-semibold text-text-primary">{toolLabel(toolTypeKey)}</p>
                <p className="text-xs text-text-secondary">{breakdown.successfulGenerations} successful generations (all-time)</p>
              </div>
              <div className="mt-2">
                <SegmentedBar breakdown={breakdown} />
              </div>
            </div>
          ))}
        </div>
      )}

      <div>
        <Button type="button" variant="secondary" onClick={refresh} disabled={refreshing} className="text-xs">
          {refreshing ? "Refreshing..." : "Refresh"}
        </Button>
      </div>

      {/* Phase 35/36's real, already-measured result -- a point-in-time
          snapshot from the committed eval, not a live number (the eval
          itself isn't runtime infrastructure, so there's nothing to
          recompute here without re-running it). Dated so it's obvious
          this can go stale and isn't pretending to be live. */}
      <div className="rounded-md border border-border-subtle bg-bg-surface-alt p-3">
        <p className="text-xs font-medium text-text-secondary">Last prompt eval (2026-09-26, not live)</p>
        <p className="mt-1 text-xs text-text-secondary">
          Title Generator: <span className="text-text-primary">3.0 / 3</span> quality, 24/24 valid (hillclimb v2). Description
          Generator: <span className="text-text-primary">3.0 / 3</span> quality, already at ceiling, untouched. Full report:{" "}
          <code className="text-text-muted">backend/.claude/hillclimb/ai-title-description/report.html</code>.
        </p>
      </div>

      <p className="text-xs text-text-muted">
        Copied/Refined/Regenerated are inferred from what each user does after a generation (Phase 37) -- a first-party proxy
        for &quot;was this good,&quot; never the prompt or response text itself.
      </p>
    </div>
  );
}
