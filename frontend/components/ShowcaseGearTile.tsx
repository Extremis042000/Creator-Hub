"use client";

import { useEffect, useState } from "react";
import { ShowcaseTile } from "./ShowcaseTile";
import { buildAffiliateRedirectUrl, type PublicAffiliateProduct } from "@/lib/api";
import { getOrCreateSessionRef } from "@/lib/session";

/**
 * Gear is the one showcase category whose real link needs a
 * client-generated session ref (see AffiliateProductCard, the full
 * /gear page's version of this same tile) -- everything else in
 * HomeShowcase links to a static internal/external URL known at
 * server-render time.
 */
export default function ShowcaseGearTile({ product }: { product: PublicAffiliateProduct }) {
  const [href, setHref] = useState<string | null>(null);

  useEffect(() => {
    setHref(buildAffiliateRedirectUrl(product.id, getOrCreateSessionRef()));
  }, [product.id]);

  return (
    <a href={href ?? "#"} target="_blank" rel="nofollow sponsored noopener" className="group block h-full">
      <ShowcaseTile
        title={product.name}
        badge={product.category ?? undefined}
        meta={product.priceInfo ?? product.brand ?? undefined}
        cta="View Deal →"
      />
    </a>
  );
}
