import ToolCard from "@/components/ToolCard";
import ComingSoonToolCard from "@/components/ComingSoonToolCard";
import { buildMetadata } from "@/lib/seo";
import { TOOLS } from "@/lib/tools";
import { fetchEnabledFeatureFlags, fetchPublicTools } from "@/lib/api";

export const metadata = buildMetadata({
  title: "All Tools",
  description:
    "Free tools for competitive gaming players and creators: sensitivity converters, KD tracking, title and description generators.",
  path: "/tools",
});

export default async function ToolsDirectoryPage() {
  const [comingSoonKeys, publicTools] = await Promise.all([
    fetchEnabledFeatureFlags(),
    fetchPublicTools(),
  ]);
  const premiumBySlug = new Map(publicTools.map((t) => [t.slug, t.premiumOnly]));
  // Coming-soon and premium tools promoted to the front -- better
  // visibility for what's driving future subscription revenue.
  const sortedTools = TOOLS.map((tool) => ({
    ...tool,
    premiumOnly: premiumBySlug.get(tool.slug) ?? false,
  })).sort((a, b) => Number(b.premiumOnly) - Number(a.premiumOnly));

  return (
    <div className="mx-auto max-w-6xl px-4 py-12">
      <h1 className="text-3xl font-bold tracking-tight">All Tools</h1>
      <p className="mt-2 text-text-secondary">
        Every tool is free, works instantly, and needs no signup.
      </p>

      <div className="mt-8 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
        {comingSoonKeys.map((key) => (
          <ComingSoonToolCard key={key} name={key} />
        ))}
        {sortedTools.map((tool) => (
          <ToolCard key={tool.slug} tool={tool} />
        ))}
      </div>
    </div>
  );
}
