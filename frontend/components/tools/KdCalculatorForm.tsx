"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { CopyButton } from "@/components/ui/CopyButton";
import { Field, Input } from "@/components/ui/Input";
import { trackEvent } from "@/lib/analytics";
import { CategoryBadge } from "./CategoryBadge";
import {
  ApiError,
  calculateKd,
  type ApiFieldError,
  type KdCalculatorResponse,
} from "@/lib/api";

export default function KdCalculatorForm() {
  const [kills, setKills] = useState("");
  const [deaths, setDeaths] = useState("");
  const [result, setResult] = useState<KdCalculatorResponse | null>(null);
  const [fieldErrors, setFieldErrors] = useState<ApiFieldError[]>([]);
  const [loading, setLoading] = useState(false);
  const [shareUrl, setShareUrl] = useState<string | null>(null);

  function errorFor(field: string) {
    return fieldErrors.find((e) => e.field === field)?.message;
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setFieldErrors([]);
    setLoading(true);
    try {
      const response = await calculateKd({
        kills: Number(kills),
        deaths: Number(deaths),
      });
      setResult(response);
      setShareUrl(null);
      trackEvent("tool_used", { tool_type: "kd_calculator" });
    } catch (err) {
      if (err instanceof ApiError && err.body.fieldErrors) {
        setFieldErrors(err.body.fieldErrors);
      }
      setResult(null);
    } finally {
      setLoading(false);
    }
  }

  async function handleShare() {
    if (!kills || !deaths) return;
    const response = await calculateKd({
      kills: Number(kills),
      deaths: Number(deaths),
      save: true,
    });
    setResult(response);
    if (response.shareToken) {
      setShareUrl(`${window.location.origin}/results/${response.shareToken}`);
      trackEvent("tool_shared", { tool_type: "kd_calculator" });
    }
  }

  return (
    <div className="grid gap-6 md:grid-cols-2">
      <Card>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <Field label="Kills" htmlFor="kills" error={errorFor("kills")}>
            <Input
              id="kills"
              type="number"
              min={0}
              inputMode="numeric"
              value={kills}
              onChange={(e) => setKills(e.target.value)}
              required
              error={errorFor("kills")}
            />
          </Field>
          <Field label="Deaths" htmlFor="deaths" error={errorFor("deaths")}>
            <Input
              id="deaths"
              type="number"
              min={0}
              inputMode="numeric"
              value={deaths}
              onChange={(e) => setDeaths(e.target.value)}
              required
              error={errorFor("deaths")}
            />
          </Field>
          <Button type="submit" disabled={loading}>
            {loading ? "Calculating..." : "Calculate"}
          </Button>
        </form>
      </Card>

      <Card className="flex flex-col justify-center gap-4">
        {!result && (
          <p className="text-center text-text-muted">
            Enter your kills and deaths to see your KD ratio.
          </p>
        )}
        {result && (
          <>
            <div className="text-center">
              <p className="font-mono text-4xl font-bold text-text-primary">
                {result.kdRatio !== null ? result.displayValue : result.displayValue}
              </p>
              <div className="mt-3 flex justify-center">
                <CategoryBadge category={result.performanceCategory} />
              </div>
            </div>
            <div className="flex justify-center gap-3">
              <CopyButton value={result.displayValue} label="Copy result" />
              <Button type="button" variant="secondary" onClick={handleShare}>
                Get share link
              </Button>
            </div>
            {shareUrl && (
              <div className="flex items-center gap-2 rounded-md border border-border-subtle bg-bg-surface-alt px-3 py-2">
                <span className="flex-1 truncate font-mono text-xs text-text-secondary">
                  {shareUrl}
                </span>
                <CopyButton value={shareUrl} label="Copy link" />
              </div>
            )}
          </>
        )}
      </Card>
    </div>
  );
}
