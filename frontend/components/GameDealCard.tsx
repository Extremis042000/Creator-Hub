import { Card } from "./ui/Card";
import type { GameDeal } from "@/lib/api";

/**
 * `compact` is used on the homepage teaser strip, where Deals is
 * deliberately the quietest section on the page -- the free tools are
 * the actual product and should win the visual hierarchy. The full
 * /deals page always uses the default (non-compact) size.
 */
export default function GameDealCard({ deal, compact = false }: { deal: GameDeal; compact?: boolean }) {
  const isFree = deal.salePriceUsd === 0;

  return (
    <Card className={`relative flex h-full flex-col overflow-hidden ${compact ? "gap-1.5 p-3" : "gap-2"}`}>
      <span
        className={`absolute z-10 rounded-full font-bold text-white [animation:glow-pulse_2.2s_ease-in-out_infinite] ${
          compact ? "right-2 top-2 px-1.5 py-0.5 text-[10px] opacity-90" : "right-3 top-3 px-2 py-0.5 text-xs"
        } ${isFree ? "bg-status-high" : "bg-brand-primary"}`}
      >
        🔥 {deal.savingsPercent}% OFF
      </span>

      {deal.thumbnailUrl && (
        <div className={`overflow-hidden ${compact ? "-mx-3 -mt-3" : "-mx-5 -mt-5"}`}>
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={deal.thumbnailUrl}
            alt={deal.title}
            className={`w-full object-cover transition-transform duration-300 hover:scale-105 ${compact ? "h-14" : "h-28"}`}
          />
        </div>
      )}

      <h3 className={`line-clamp-1 font-semibold text-text-primary ${compact ? "mt-0.5 text-sm" : "mt-1 text-base"}`}>
        {deal.title}
      </h3>

      <div className="flex items-baseline gap-2">
        <span className={`font-bold text-status-high ${compact ? "text-sm" : "text-lg"}`}>
          {isFree ? "FREE" : `$${deal.salePriceUsd.toFixed(2)}`}
        </span>
        {deal.normalPriceUsd > deal.salePriceUsd && (
          <span className={`text-text-muted line-through ${compact ? "text-xs" : "text-sm"}`}>
            ${deal.normalPriceUsd.toFixed(2)}
          </span>
        )}
      </div>

      {!compact && deal.steamRatingPercent !== null && (
        <p className="text-xs text-text-secondary">
          ⭐ {deal.steamRatingPercent}% positive on Steam · {deal.storeName}
        </p>
      )}

      <a
        href={deal.dealUrl}
        target="_blank"
        rel="noopener"
        className={`mt-auto inline-flex items-center justify-center rounded-md bg-brand-primary font-semibold text-white transition-colors hover:bg-brand-primary-hover ${
          compact ? "px-2 py-1.5 text-xs" : "px-3 py-2 text-sm"
        }`}
      >
        {compact ? "Get Deal →" : `Get on ${deal.storeName} →`}
      </a>
    </Card>
  );
}
