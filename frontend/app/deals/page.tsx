import Link from "next/link";
import GameDealCard from "@/components/GameDealCard";
import AffiliateProductCard from "@/components/AffiliateProductCard";
import { buildMetadata } from "@/lib/seo";
import { fetchActiveAffiliateProducts, fetchGameDeals } from "@/lib/api";

export const metadata = buildMetadata({
  title: "Deals",
  description: "Live PC game deals on Steam and Epic Games Store, plus recommended creator gear.",
  path: "/deals",
});

export default async function DealsPage() {
  const [deals, gear] = await Promise.all([fetchGameDeals(), fetchActiveAffiliateProducts()]);

  return (
    <div className="mx-auto max-w-6xl px-4 py-12">
      <div className="grid gap-10 lg:grid-cols-2">
        <section>
          <h1 className="text-2xl font-bold tracking-tight">🔥 Hot Game Deals</h1>
          <p className="mt-2 text-sm text-text-secondary">
            Live discounts on Steam and Epic Games Store, refreshed automatically. Every link
            goes straight to the real store page — no third-party redirects for Steam, and
            Epic deals use CheapShark's own official redirect. Only genuinely well-rated games
            (70%+ positive, real review volume) make the list.
          </p>

          {deals.length === 0 ? (
            <p className="mt-8 text-text-secondary">No deals available right now — check back soon.</p>
          ) : (
            <div className="mt-6 grid gap-4 sm:grid-cols-2">
              {deals.map((deal) => (
                <GameDealCard key={deal.dealUrl} deal={deal} />
              ))}
            </div>
          )}
        </section>

        <section>
          <h2 className="text-2xl font-bold tracking-tight">Recommended Gear</h2>
          <p className="mt-2 text-sm text-text-secondary">
            Gear we actually recommend for competitive play and content creation.
          </p>
          <p className="mt-3 rounded-md border border-border-subtle bg-bg-surface px-4 py-3 text-xs text-text-muted">
            Disclosure: some links here are affiliate links. If you buy through them, EXTREMIS
            Creator Hub may earn a small commission at no extra cost to you. We only recommend
            gear we believe is genuinely good.
          </p>

          {gear.length === 0 ? (
            <p className="mt-8 text-text-secondary">No gear recommendations yet — check back soon.</p>
          ) : (
            <div className="mt-6 grid gap-4 sm:grid-cols-2">
              {gear.map((product) => (
                <AffiliateProductCard key={product.id} product={product} />
              ))}
            </div>
          )}
        </section>
      </div>

      <p className="mt-10 text-center text-xs text-text-muted">
        Looking for the old standalone gear page?{" "}
        <Link href="/gear" className="text-brand-accent hover:underline">
          It's still here
        </Link>
        .
      </p>
    </div>
  );
}
