import GameDealCard from "@/components/GameDealCard";
import { buildMetadata } from "@/lib/seo";
import { fetchGameDeals } from "@/lib/api";

export const metadata = buildMetadata({
  title: "Hot Game Deals",
  description: "Live PC game deals and discounts on Steam, refreshed automatically.",
  path: "/deals",
});

export default async function DealsPage() {
  const deals = await fetchGameDeals();

  return (
    <div className="mx-auto max-w-6xl px-4 py-12">
      <h1 className="text-3xl font-bold tracking-tight">🔥 Hot Game Deals</h1>
      <p className="mt-2 max-w-2xl text-text-secondary">
        Live Steam discounts, refreshed automatically throughout the day. Every link goes
        straight to the official Steam store page — no third-party redirects.
      </p>

      {deals.length === 0 ? (
        <p className="mt-10 text-text-secondary">No deals available right now — check back soon.</p>
      ) : (
        <div className="mt-8 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {deals.map((deal) => (
            <GameDealCard key={deal.steamStoreUrl} deal={deal} />
          ))}
        </div>
      )}
    </div>
  );
}
