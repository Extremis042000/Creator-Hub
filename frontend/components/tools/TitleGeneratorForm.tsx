"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { CopyButton } from "@/components/ui/CopyButton";
import { Field, Input, Select } from "@/components/ui/Input";
import RefineBox from "@/components/tools/RefineBox";
import { trackEvent } from "@/lib/analytics";
import {
  ApiError,
  generateTitles,
  refineTitles,
  TONE_LABELS,
  VIDEO_TYPE_LABELS,
  type ApiFieldError,
  type TitleGeneratorResponse,
  type Tone,
  type VideoType,
} from "@/lib/api";

const VIDEO_TYPES = Object.keys(VIDEO_TYPE_LABELS) as VideoType[];
const TONES = Object.keys(TONE_LABELS) as Tone[];

export default function TitleGeneratorForm() {
  const [game, setGame] = useState("");
  const [topic, setTopic] = useState("");
  const [videoType, setVideoType] = useState<VideoType>("HIGHLIGHT");
  const [tone, setTone] = useState<Tone>("HYPE");
  const [keywordsInput, setKeywordsInput] = useState("");
  const [result, setResult] = useState<TitleGeneratorResponse | null>(null);
  const [fieldErrors, setFieldErrors] = useState<ApiFieldError[]>([]);
  const [loading, setLoading] = useState(false);

  function errorFor(field: string) {
    return fieldErrors.find((e) => e.field === field)?.message;
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setFieldErrors([]);
    setLoading(true);
    try {
      const keywords = keywordsInput
        .split(",")
        .map((k) => k.trim())
        .filter(Boolean)
        .slice(0, 10);
      const response = await generateTitles({ game, topic, videoType, tone, keywords });
      setResult(response);
      trackEvent("tool_used", { tool_type: "gaming_title_generator" });
    } catch (err) {
      if (err instanceof ApiError && err.body.fieldErrors) setFieldErrors(err.body.fieldErrors);
      setResult(null);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="grid gap-6 md:grid-cols-2">
      <Card>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <Field label="Game" htmlFor="game" error={errorFor("game")}>
            <Input
              id="game"
              value={game}
              onChange={(e) => setGame(e.target.value)}
              required
              error={errorFor("game")}
            />
          </Field>
          <Field label="Video topic" htmlFor="topic" error={errorFor("topic")}>
            <Input
              id="topic"
              value={topic}
              onChange={(e) => setTopic(e.target.value)}
              required
              error={errorFor("topic")}
            />
          </Field>
          <Field label="Video type" htmlFor="videoType">
            <Select
              id="videoType"
              value={videoType}
              onChange={(e) => setVideoType(e.target.value as VideoType)}
            >
              {VIDEO_TYPES.map((v) => (
                <option key={v} value={v}>
                  {VIDEO_TYPE_LABELS[v]}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Tone" htmlFor="tone">
            <Select id="tone" value={tone} onChange={(e) => setTone(e.target.value as Tone)}>
              {TONES.map((t) => (
                <option key={t} value={t}>
                  {TONE_LABELS[t]}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Keywords (comma-separated, optional)" htmlFor="keywords">
            <Input
              id="keywords"
              value={keywordsInput}
              onChange={(e) => setKeywordsInput(e.target.value)}
              placeholder="ace, clutch"
            />
          </Field>
          <Button type="submit" disabled={loading}>
            {loading ? "Generating..." : "Generate titles"}
          </Button>
        </form>
      </Card>

      <Card className="flex flex-col gap-4">
        {!result && <p className="text-center text-text-muted">Titles will appear here.</p>}
        {result && (
          <>
            <div>
              <h3 className="text-sm font-semibold text-text-secondary">Titles</h3>
              <ul className="mt-2 flex flex-col gap-2">
                {result.titles.map((title) => (
                  <li key={title} className="flex items-center justify-between gap-2">
                    <span className="text-sm">{title}</span>
                    <CopyButton value={title} label="Copy" />
                  </li>
                ))}
              </ul>
            </div>
            <div>
              <h3 className="text-sm font-semibold text-text-secondary">Short-form titles</h3>
              <ul className="mt-2 flex flex-col gap-2">
                {result.shortFormTitles.map((title) => (
                  <li key={title} className="flex items-center justify-between gap-2">
                    <span className="text-sm">{title}</span>
                    <CopyButton value={title} label="Copy" />
                  </li>
                ))}
              </ul>
            </div>
            {result.refineSessionId && (
              <RefineBox sessionId={result.refineSessionId} onRefine={refineTitles} onResult={setResult} />
            )}
          </>
        )}
      </Card>
    </div>
  );
}
