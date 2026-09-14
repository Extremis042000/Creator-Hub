"use client";

import { useEffect, useState } from "react";
import { Card } from "./ui/Card";
import { buildAffiliateRedirectUrl, type PublicAffiliateProduct } from "@/lib/api";
import { getOrCreateSessionRef } from "@/lib/session";

export default function AffiliateProductCard({ product }: { product: PublicAffiliateProduct }) {
  const [href, setHref] = useState<string | null>(null);

  useEffect(() => {
    setHref(buildAffiliateRedirectUrl(product.id, getOrCreateSessionRef()));
  }, [product.id]);

  return (
    <Card className="flex h-full flex-col gap-2 overflow-hidden">
      {product.imageUrl && (
        <div className="-mx-5 -mt-5 overflow-hidden">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={product.imageUrl}
            alt={product.name}
            className="h-28 w-full object-cover"
          />
        </div>
      )}
      {product.category && (
        <span className="w-fit rounded-full border border-border-strong px-2 py-0.5 text-xs uppercase tracking-wide text-text-secondary">
          {product.category}
        </span>
      )}
      <h3 className="text-lg font-semibold text-text-primary">{product.name}</h3>
      {product.brand && <p className="text-sm text-text-secondary">{product.brand}</p>}
      {product.priceInfo && <p className="text-sm font-medium text-text-primary">{product.priceInfo}</p>}
      {product.merchant && <p className="text-xs text-text-muted">via {product.merchant}</p>}
      {product.disclosureText && <p className="text-xs text-text-muted">{product.disclosureText}</p>}
      <a
        href={href ?? "#"}
        target="_blank"
        rel="nofollow sponsored noopener"
        className="mt-auto text-sm font-medium text-brand-accent hover:underline"
      >
        View Deal &rarr;
      </a>
    </Card>
  );
}
