"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ConfidenceBadge } from "@/components/ui/ConfidenceBadge";
import { Disclaimer } from "@/components/ui/Disclaimer";
import { trackEvent } from "@/lib/analytics";
import {
  recommendBgmiSensitivity,
  SCOPE_LEVEL_LABELS,
  type BgmiSensitivityResponse,
} from "@/lib/api";

export default function BgmiSensitivityForm() {
  const [usesGyroscope, setUsesGyroscope] = useState(true);
  const [result, setResult] = useState<BgmiSensitivityResponse | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setLoading(true);
    try {
      const response = await recommendBgmiSensitivity({ usesGyroscope });
      setResult(response);
      trackEvent("tool_used", { tool_type: "bgmi_sensitivity_helper" });
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <fieldset className="flex flex-col gap-2">
            <legend className="text-sm font-medium text-text-secondary">
              Do you use gyroscope?
            </legend>
            <div className="flex gap-3">
              <Button
                type="button"
                variant={usesGyroscope ? "primary" : "secondary"}
                onClick={() => setUsesGyroscope(true)}
              >
                Yes, I use gyroscope
              </Button>
              <Button
                type="button"
                variant={!usesGyroscope ? "primary" : "secondary"}
                onClick={() => setUsesGyroscope(false)}
              >
                No gyroscope
              </Button>
            </div>
          </fieldset>
          <Button type="submit" disabled={loading} className="w-fit">
            {loading ? "Loading..." : "Get recommendations"}
          </Button>
        </form>
      </Card>

      {result && (
        <Card className="flex flex-col gap-4">
          <Disclaimer text={result.disclaimer} />
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-border-subtle text-text-secondary">
                  <th className="py-2 pr-4">Scope</th>
                  <th className="py-2 pr-4">Setting</th>
                  <th className="py-2 pr-4">Range</th>
                  <th className="py-2 pr-4">Starting value</th>
                  <th className="py-2">Confidence</th>
                </tr>
              </thead>
              <tbody>
                {result.recommendations.map((rec) => (
                  <tr key={rec.scopeLevel} className="border-b border-border-subtle/50">
                    <td className="py-2 pr-4 font-medium">
                      {SCOPE_LEVEL_LABELS[rec.scopeLevel]}
                    </td>
                    <td className="py-2 pr-4 text-text-secondary">{rec.settingName}</td>
                    <td className="py-2 pr-4 font-mono">
                      {rec.recommendedMin != null && rec.recommendedMax != null
                        ? `${rec.recommendedMin}-${rec.recommendedMax}%`
                        : "—"}
                    </td>
                    <td className="py-2 pr-4 font-mono">
                      {rec.recommendedStartingValue != null
                        ? `${rec.recommendedStartingValue}%`
                        : "—"}
                    </td>
                    <td className="py-2">
                      <ConfidenceBadge confidence={rec.confidence} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </div>
  );
}
