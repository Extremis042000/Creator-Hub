import Link from "next/link";
import { ShowcaseSlideShell } from "@/components/ShowcaseSlideShell";
import { ShowcaseTile } from "@/components/ShowcaseTile";
import ShowcaseGearTile from "@/components/ShowcaseGearTile";
import type { ShowcaseSlide } from "@/components/HomeShowcase";
import type { ToolSummary } from "@/lib/tools";
import type { GameDeal, PublicAffiliateProduct, PublicProduct } from "@/lib/api";

const MAX_ITEMS = 3;

function formatProductPrice(cents: number, currency: string) {
  return cents === 0 ? "FREE" : `${(cents / 100).toFixed(2)} ${currency}`;
}

/**
 * Six slide builders for the homepage hero's HomeShowcase carousel --
 * one per rotating section (docs: the founder's own ordering). Each
 * returns null when that section has nothing to show, so
 * buildHomeShowcaseSlides can filter empty ones out entirely rather
 * than rotating through a blank slide. Every item is capped at
 * MAX_ITEMS -- this is a teaser, not the full listing (each slide's
 * "See all" link is where the complete list lives).
 */

function allToolsSlide(tools: (ToolSummary & { premiumOnly?: boolean })[]): ShowcaseSlide | null {
  if (tools.length === 0) return null;
  return {
    key: "all-tools",
    label: "All Tools",
    node: (
      <ShowcaseSlideShell icon="🧰" title="All Tools" href="/tools" linkLabel="Browse all →">
        {tools.slice(0, MAX_ITEMS).map((tool) => (
          <Link key={tool.slug} href={`/tools/${tool.slug}`} className="group block h-full">
            <ShowcaseTile title={tool.name} badge={tool.category} cta="Try it →" />
          </Link>
        ))}
      </ShowcaseSlideShell>
    ),
  };
}

function premiumToolsSlide(tools: (ToolSummary & { premiumOnly?: boolean })[]): ShowcaseSlide | null {
  const premium = tools.filter((t) => t.premiumOnly);
  if (premium.length === 0) return null;
  return {
    key: "premium-tools",
    label: "Premium Tools",
    node: (
      <ShowcaseSlideShell icon="⭐" title="Premium Tools" href="/tools" linkLabel="Browse all →">
        {premium.slice(0, MAX_ITEMS).map((tool) => (
          <Link key={tool.slug} href={`/tools/${tool.slug}`} className="group block h-full">
            <ShowcaseTile title={tool.name} badge="Premium" cta="Try it →" />
          </Link>
        ))}
      </ShowcaseSlideShell>
    ),
  };
}

function comingSoonSlide(keys: string[]): ShowcaseSlide | null {
  if (keys.length === 0) return null;
  return {
    key: "coming-soon",
    label: "Coming Soon",
    node: (
      <ShowcaseSlideShell icon="🚀" title="Coming Soon">
        {keys.slice(0, MAX_ITEMS).map((name) => (
          <ShowcaseTile key={name} title={name} badge="Coming soon" meta="Free trial on launch" />
        ))}
      </ShowcaseSlideShell>
    ),
  };
}

function hotGameDealsSlide(deals: GameDeal[]): ShowcaseSlide | null {
  if (deals.length === 0) return null;
  return {
    key: "hot-game-deals",
    label: "Hot Game Deals",
    node: (
      <ShowcaseSlideShell icon="🔥" title="Hot Game Deals" href="/deals" linkLabel="See all deals →">
        {deals.slice(0, MAX_ITEMS).map((deal) => (
          <a key={deal.dealUrl} href={deal.dealUrl} target="_blank" rel="noopener" className="group block h-full">
            <ShowcaseTile
              title={deal.title}
              badge={`${deal.savingsPercent}% OFF`}
              meta={deal.salePriceUsd === 0 ? "FREE" : `$${deal.salePriceUsd.toFixed(2)}`}
              cta="Get Deal →"
            />
          </a>
        ))}
      </ShowcaseSlideShell>
    ),
  };
}

function recommendedGearSlide(products: PublicAffiliateProduct[]): ShowcaseSlide | null {
  if (products.length === 0) return null;
  return {
    key: "recommended-gear",
    label: "Recommended Gear",
    node: (
      <ShowcaseSlideShell icon="🎮" title="Recommended Gear" href="/gear" linkLabel="See all gear →">
        {products.slice(0, MAX_ITEMS).map((product) => (
          <ShowcaseGearTile key={product.id} product={product} />
        ))}
      </ShowcaseSlideShell>
    ),
  };
}

function ourStoreSlide(products: PublicProduct[]): ShowcaseSlide | null {
  if (products.length === 0) return null;
  return {
    key: "our-store",
    label: "Our Store",
    node: (
      <ShowcaseSlideShell icon="🛒" title="Our Store" href="/store" linkLabel="Visit store →">
        {products.slice(0, MAX_ITEMS).map((product) => (
          <Link key={product.id} href="/store" className="group block h-full">
            <ShowcaseTile
              title={product.name}
              badge={product.categorySlug}
              meta={formatProductPrice(product.priceCents, product.currency)}
              cta="View →"
            />
          </Link>
        ))}
      </ShowcaseSlideShell>
    ),
  };
}

export function buildHomeShowcaseSlides(data: {
  tools: (ToolSummary & { premiumOnly?: boolean })[];
  comingSoonKeys: string[];
  gameDeals: GameDeal[];
  gearProducts: PublicAffiliateProduct[];
  storeProducts: PublicProduct[];
}): ShowcaseSlide[] {
  // Order is the founder's own: All tools -> All premium tools -> Coming
  // Soon -> Hot Game Deals -> Recommended Gear -> Our Store. Empty
  // sections are filtered out rather than shown as a blank slide.
  return [
    allToolsSlide(data.tools),
    premiumToolsSlide(data.tools),
    comingSoonSlide(data.comingSoonKeys),
    hotGameDealsSlide(data.gameDeals),
    recommendedGearSlide(data.gearProducts),
    ourStoreSlide(data.storeProducts),
  ].filter((slide): slide is ShowcaseSlide => slide !== null);
}
