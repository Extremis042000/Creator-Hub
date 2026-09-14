import type { Metadata } from "next";
import Link from "next/link";
import ToolCard from "@/components/ToolCard";
import HomeComingSoonCard from "@/components/HomeComingSoonCard";
import GameDealCard from "@/components/GameDealCard";
import { SITE_NAME, SITE_URL } from "@/lib/seo";
import { TOOLS } from "@/lib/tools";
import { fetchEnabledFeatureFlags, fetchGameDeals, fetchPublicTools } from "@/lib/api";

// No title override here — the layout's title.default already IS the
// site name; setting one would run it through the "%s | SITE_NAME"
// template and duplicate the name. Canonical/OG/Twitter still need
// setting explicitly per page.
export const metadata: Metadata = {
  alternates: { canonical: SITE_URL },
  openGraph: { url: SITE_URL, siteName: SITE_NAME, type: "website" },
};

export default async function HomePage() {
  const [publicTools, comingSoonKeys, gameDeals] = await Promise.all([
    fetchPublicTools(),
    fetchEnabledFeatureFlags(),
    fetchGameDeals(),
  ]);
  const topDeals = gameDeals.slice(0, 3);
  const premiumBySlug = new Map(publicTools.map((t) => [t.slug, t.premiumOnly]));
  // Premium tools promoted to the front of the grid -- subscription
  // upsell visibility takes priority over the free tools' original order.
  const sortedTools = TOOLS.map((tool) => ({
    ...tool,
    premiumOnly: premiumBySlug.get(tool.slug) ?? false,
  })).sort((a, b) => Number(b.premiumOnly) - Number(a.premiumOnly));

  return (
    <>
      <section className="mx-auto max-w-6xl px-4 py-20">
        <div className={`grid gap-10 ${topDeals.length > 0 ? "lg:grid-cols-2 lg:items-center" : ""}`}>
          <div className="text-center lg:text-left">
            <h1 className="mx-auto max-w-xl text-4xl font-bold tracking-tight sm:text-5xl lg:mx-0">
              Free tools for competitive players &amp; gaming creators
            </h1>
            <p className="mx-auto mt-4 max-w-xl text-text-secondary lg:mx-0">
              Free tools built for players who compete and creators who grind — no signup, ever.
            </p>
            <Link
              href="/tools"
              className="mt-8 inline-block rounded-md bg-brand-primary px-6 py-3 font-semibold text-white transition-colors hover:bg-brand-primary-hover"
            >
              Browse all tools &rarr;
            </Link>
          </div>

          {topDeals.length > 0 && (
            <div>
              <div className="flex items-center justify-between">
                <h2 className="text-base font-semibold text-text-secondary">🔥 Hot Game Deals</h2>
                <Link href="/deals" className="text-xs font-medium text-brand-accent hover:underline">
                  See all deals &rarr;
                </Link>
              </div>
              <div className="mt-3 grid gap-3 sm:grid-cols-2">
                {topDeals.map((deal) => (
                  <GameDealCard key={deal.dealUrl} deal={deal} compact />
                ))}
              </div>
            </div>
          )}
        </div>
      </section>

      {comingSoonKeys.length > 0 && (
        <section className="border-t border-border-subtle">
          <div className="mx-auto max-w-6xl px-4 py-12">
            <h2 className="text-2xl font-bold tracking-tight">🚀 Coming Soon</h2>
            <p className="mt-2 text-text-secondary">
              New tools are in the works — the first release gets a free trial, on us.
            </p>
            <div className="mt-6 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {comingSoonKeys.map((key) => (
                <HomeComingSoonCard key={key} name={key} />
              ))}
            </div>
          </div>
        </section>
      )}

      <section className="border-t border-border-subtle">
        <div className="mx-auto max-w-6xl px-4 py-12">
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {sortedTools.map((tool) => (
              <ToolCard key={tool.slug} tool={tool} />
            ))}
          </div>
        </div>
      </section>

      <section className="border-t border-border-subtle">
        <div className="mx-auto grid max-w-6xl gap-8 px-4 py-16 sm:grid-cols-3">
          <div>
            <h2 className="font-semibold">Free, always</h2>
            <p className="mt-1 text-sm text-text-secondary">
              Every tool works fully, with no signup and no paywall.
            </p>
          </div>
          <div>
            <h2 className="font-semibold">No account needed</h2>
            <p className="mt-1 text-sm text-text-secondary">
              Use any tool instantly. Sign in only if you want to save results.
            </p>
          </div>
          <div>
            <h2 className="font-semibold">Built by a gaming creator</h2>
            <p className="mt-1 text-sm text-text-secondary">
              From the team behind{" "}
              <span className="text-text-primary">EXTREMIS Plays</span>.
            </p>
          </div>
        </div>
      </section>
    </>
  );
}
