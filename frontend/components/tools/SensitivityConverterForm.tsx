"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { CopyButton } from "@/components/ui/CopyButton";
import { Field, Input, Select } from "@/components/ui/Input";
import { trackEvent } from "@/lib/analytics";
import {
  ApiError,
  convertSensitivity,
  SUPPORTED_GAME_LABELS,
  type ApiFieldError,
  type SensitivityConversionResponse,
  type SupportedGame,
} from "@/lib/api";

const GAMES = Object.keys(SUPPORTED_GAME_LABELS) as SupportedGame[];

export default function SensitivityConverterForm() {
  const [sourceGame, setSourceGame] = useState<SupportedGame>("CSGO");
  const [targetGame, setTargetGame] = useState<SupportedGame>("VALORANT");
  const [dpi, setDpi] = useState("800");
  const [sourceSensitivity, setSourceSensitivity] = useState("");
  const [result, setResult] = useState<SensitivityConversionResponse | null>(null);
  const [fieldErrors, setFieldErrors] = useState<ApiFieldError[]>([]);
  const [businessError, setBusinessError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  function errorFor(field: string) {
    return fieldErrors.find((e) => e.field === field)?.message;
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setFieldErrors([]);
    setBusinessError(null);
    setLoading(true);
    try {
      const response = await convertSensitivity({
        sourceGame,
        targetGame,
        dpi: Number(dpi),
        sourceSensitivity: Number(sourceSensitivity),
      });
      setResult(response);
      trackEvent("tool_used", { tool_type: "valorant_sensitivity_converter" });
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.body.fieldErrors) setFieldErrors(err.body.fieldErrors);
        if (err.body.error === "BUSINESS_RULE_VIOLATION") setBusinessError(err.body.message);
      }
      setResult(null);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="grid gap-6 md:grid-cols-2">
      <Card>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <Field label="From game" htmlFor="sourceGame">
            <Select
              id="sourceGame"
              value={sourceGame}
              onChange={(e) => setSourceGame(e.target.value as SupportedGame)}
            >
              {GAMES.map((g) => (
                <option key={g} value={g}>
                  {SUPPORTED_GAME_LABELS[g]}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="To game" htmlFor="targetGame">
            <Select
              id="targetGame"
              value={targetGame}
              onChange={(e) => setTargetGame(e.target.value as SupportedGame)}
            >
              {GAMES.map((g) => (
                <option key={g} value={g}>
                  {SUPPORTED_GAME_LABELS[g]}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="DPI" htmlFor="dpi" error={errorFor("dpi")}>
            <Input
              id="dpi"
              type="number"
              min={100}
              max={25600}
              value={dpi}
              onChange={(e) => setDpi(e.target.value)}
              required
              error={errorFor("dpi")}
            />
          </Field>
          <Field
            label="Your current sensitivity"
            htmlFor="sourceSensitivity"
            error={errorFor("sourceSensitivity")}
          >
            <Input
              id="sourceSensitivity"
              type="number"
              step="any"
              min={0}
              value={sourceSensitivity}
              onChange={(e) => setSourceSensitivity(e.target.value)}
              required
              error={errorFor("sourceSensitivity")}
            />
          </Field>
          <Button type="submit" disabled={loading}>
            {loading ? "Converting..." : "Convert"}
          </Button>
        </form>
      </Card>

      <Card className="flex flex-col justify-center gap-4">
        {businessError && (
          <p className="text-center text-sm text-brand-primary">{businessError}</p>
        )}
        {!result && !businessError && (
          <p className="text-center text-text-muted">
            Enter your DPI and sensitivity to see the converted value.
          </p>
        )}
        {result && (
          <>
            <div className="text-center">
              <p className="font-mono text-4xl font-bold text-text-primary">
                {result.targetSensitivity}
              </p>
              <p className="mt-1 text-sm text-text-secondary">
                {SUPPORTED_GAME_LABELS[targetGame]} sensitivity at {dpi} DPI
              </p>
              {result.conversionNote && (
                <p className="mt-2 text-xs text-text-muted">{result.conversionNote}</p>
              )}
            </div>
            <div className="flex justify-center">
              <CopyButton value={String(result.targetSensitivity)} label="Copy result" />
            </div>
          </>
        )}
      </Card>
    </div>
  );
}
