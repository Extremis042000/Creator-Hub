"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { CopyButton } from "@/components/ui/CopyButton";
import { Field, Input } from "@/components/ui/Input";
import RefineBox from "@/components/tools/RefineBox";
import { trackEvent } from "@/lib/analytics";
import {
  ApiError,
  generateDescription,
  refineDescription,
  reportDescriptionCopied,
  type ApiFieldError,
  type DescriptionGeneratorResponse,
} from "@/lib/api";

export default function DescriptionGeneratorForm() {
  const [game, setGame] = useState("");
  const [topic, setTopic] = useState("");
  const [channelName, setChannelName] = useState("");
  const [keywordsInput, setKeywordsInput] = useState("");
  const [socialUrl, setSocialUrl] = useState("");
  const [socialPlatform, setSocialPlatform] = useState("YouTube");
  const [result, setResult] = useState<DescriptionGeneratorResponse | null>(null);
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
      const keywords = keywordsInput.split(",").map((k) => k.trim()).filter(Boolean).slice(0, 15);
      const socialLinks = socialUrl ? [{ platform: socialPlatform, url: socialUrl }] : [];
      const response = await generateDescription({ game, topic, channelName, keywords, socialLinks });
      setResult(response);
      trackEvent("tool_used", { tool_type: "gaming_description_generator" });
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
            <Input id="game" value={game} onChange={(e) => setGame(e.target.value)} required error={errorFor("game")} />
          </Field>
          <Field label="Video topic" htmlFor="topic" error={errorFor("topic")}>
            <Input id="topic" value={topic} onChange={(e) => setTopic(e.target.value)} required error={errorFor("topic")} />
          </Field>
          <Field label="Channel name" htmlFor="channelName" error={errorFor("channelName")}>
            <Input
              id="channelName"
              value={channelName}
              onChange={(e) => setChannelName(e.target.value)}
              required
              error={errorFor("channelName")}
            />
          </Field>
          <Field label="Keywords (comma-separated, optional)" htmlFor="keywords">
            <Input
              id="keywords"
              value={keywordsInput}
              onChange={(e) => setKeywordsInput(e.target.value)}
              placeholder="bgmi highlights, solo vs squad"
            />
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Social platform" htmlFor="socialPlatform">
              <Input
                id="socialPlatform"
                value={socialPlatform}
                onChange={(e) => setSocialPlatform(e.target.value)}
              />
            </Field>
            <Field label="Social URL (optional)" htmlFor="socialUrl" error={errorFor("socialLinks[0].url")}>
              <Input
                id="socialUrl"
                value={socialUrl}
                onChange={(e) => setSocialUrl(e.target.value)}
                placeholder="https://youtube.com/@you"
                error={errorFor("socialLinks[0].url")}
              />
            </Field>
          </div>
          <Button type="submit" disabled={loading}>
            {loading ? "Generating..." : "Generate description"}
          </Button>
        </form>
      </Card>

      <Card className="flex flex-col gap-4">
        {!result && <p className="text-center text-text-muted">Your description will appear here.</p>}
        {result && (
          <>
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold text-text-secondary">Description</h3>
              <CopyButton
                value={result.description}
                label="Copy all"
                onCopy={result.refineSessionId ? () => reportDescriptionCopied(result.refineSessionId!) : undefined}
              />
            </div>
            <pre className="whitespace-pre-wrap rounded-md border border-border-subtle bg-bg-surface-alt p-3 font-mono text-xs text-text-primary">
              {result.description}
            </pre>
            <div className="flex flex-wrap gap-2">
              {result.hashtags.map((tag) => (
                <span key={tag} className="rounded-full border border-border-strong px-2 py-0.5 text-xs text-text-secondary">
                  {tag}
                </span>
              ))}
            </div>
            {result.refineSessionId && (
              <RefineBox sessionId={result.refineSessionId} onRefine={refineDescription} onResult={setResult} />
            )}
          </>
        )}
      </Card>
    </div>
  );
}
