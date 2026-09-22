"use client";

import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { fetchAiUsageToday, type AiUsageSummary } from "@/lib/api";

export default function AdminAiUsageCard({
  initialUsage,
  token,
}: {
  initialUsage: AiUsageSummary;
  token: string;
}) {
  const [usage, setUsage] = useState(initialUsage);
  const [refreshing, setRefreshing] = useState(false);

  async function refresh() {
    setRefreshing(true);
    try {
      setUsage(await fetchAiUsageToday(token));
    } finally {
      setRefreshing(false);
    }
  }

  const ceilingPercent = usage.dailyCallCeiling > 0
    ? Math.min(100, Math.round((usage.totalCalls / usage.dailyCallCeiling) * 100))
    : 0;
  const nearCeiling = ceilingPercent >= 80;

  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <Stat label="Calls today" value={usage.totalCalls} />
        <Stat label="Succeeded" value={usage.successCount} />
        <Stat label="Failed" value={usage.failureCount} />
        <Stat label="Throttled" value={usage.throttledCount} />
      </div>

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        <Stat label="Input tokens" value={usage.totalInputTokens} />
        <Stat label="Output tokens" value={usage.totalOutputTokens} />
        <Stat label="Avg latency" value={`${Math.round(usage.averageLatencyMs)} ms`} />
      </div>

      <div>
        <div className="flex items-center justify-between text-xs text-text-secondary">
          <span>Daily call ceiling</span>
          <span className={nearCeiling ? "font-semibold text-brand-primary" : ""}>
            {usage.totalCalls} / {usage.dailyCallCeiling}
          </span>
        </div>
        <div className="mt-1 h-2 w-full overflow-hidden rounded-full bg-border-subtle">
          <div
            className={`h-full rounded-full ${nearCeiling ? "bg-brand-primary" : "bg-brand-accent"}`}
            style={{ width: `${ceilingPercent}%` }}
          />
        </div>
        {nearCeiling && (
          <p className="mt-1 text-xs text-brand-primary">
            Approaching today&apos;s ceiling -- once reached, AI generation falls back to templates for everyone until the next UTC day.
          </p>
        )}
      </div>

      {Object.keys(usage.callsByProvider).length > 0 && (
        <div>
          <p className="text-xs font-medium text-text-secondary">Calls by provider</p>
          <ul className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-sm">
            {Object.entries(usage.callsByProvider).map(([provider, count]) => (
              <li key={provider} className="text-text-secondary">
                <span className="text-text-primary">{provider}</span>: {count}
              </li>
            ))}
          </ul>
        </div>
      )}

      <div>
        <Button type="button" variant="secondary" onClick={refresh} disabled={refreshing} className="text-xs">
          {refreshing ? "Refreshing..." : "Refresh"}
        </Button>
      </div>
      <p className="text-xs text-text-muted">
        Counts are calls, not dollars -- the configured AI backends don&apos;t publish per-call pricing. Never includes prompt or response text.
      </p>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: number | string }) {
  return (
    <div>
      <p className="text-xs text-text-secondary">{label}</p>
      <p className="text-lg font-semibold text-text-primary">{value}</p>
    </div>
  );
}
