"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import {
  ApiError,
  fetchActiveProducts,
  fetchOwnedProductIds,
  fetchProductDownloadUrl,
  testPurchaseProduct,
  type PublicProduct,
} from "@/lib/api";
import { getToken } from "@/lib/auth";

function formatPrice(cents: number, currency: string) {
  if (cents === 0) return "FREE";
  return `${(cents / 100).toFixed(2)} ${currency}`;
}

function ProductCard({
  product,
  token,
  owned,
  onPurchased,
}: {
  product: PublicProduct;
  token: string | null;
  owned: boolean;
  onPurchased: (productId: string) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handlePurchase() {
    if (!token) return;
    setBusy(true);
    setError(null);
    try {
      await testPurchaseProduct(token, product.id);
      onPurchased(product.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.body.message : "Purchase failed. Try again.");
    } finally {
      setBusy(false);
    }
  }

  async function handleDownload() {
    if (!token) return;
    setBusy(true);
    setError(null);
    try {
      const { downloadUrl } = await fetchProductDownloadUrl(token, product.id);
      window.open(`${process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080"}${downloadUrl}`, "_blank");
    } catch (err) {
      setError(err instanceof ApiError ? err.body.message : "Couldn't get the download link. Try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card className="flex h-full flex-col gap-2">
      <h3 className="text-lg font-semibold text-text-primary">{product.name}</h3>
      <span className="w-fit rounded-full border border-border-strong px-2 py-0.5 text-xs uppercase tracking-wide text-text-secondary">
        {product.categorySlug}
      </span>
      <p className="text-lg font-bold text-status-high">{formatPrice(product.priceCents, product.currency)}</p>

      {!token ? (
        <a
          href="/login"
          className="mt-auto rounded-md border border-border-strong px-3 py-2 text-center text-sm font-semibold text-text-secondary hover:text-text-primary"
        >
          Sign in to purchase
        </a>
      ) : owned ? (
        <Button type="button" onClick={handleDownload} disabled={busy} className="mt-auto text-sm">
          {busy ? "Preparing..." : "Download"}
        </Button>
      ) : (
        <Button type="button" onClick={handlePurchase} disabled={busy} className="mt-auto text-sm">
          {busy ? "Processing..." : "Get It (Test Mode — Free)"}
        </Button>
      )}
      {error && <p className="text-xs text-brand-primary">{error}</p>}
    </Card>
  );
}

export default function StorePage() {
  const [products, setProducts] = useState<PublicProduct[]>([]);
  const [ownedIds, setOwnedIds] = useState<Set<string>>(new Set());
  const [token, setToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const t = getToken();
    setToken(t);

    const productsPromise = fetchActiveProducts();
    const ownedPromise = t ? fetchOwnedProductIds(t) : Promise.resolve<string[]>([]);

    Promise.all([productsPromise, ownedPromise]).then(([productsResult, ownedResult]) => {
      setProducts(productsResult);
      setOwnedIds(new Set(ownedResult));
      setLoading(false);
    });
  }, []);

  function handlePurchased(productId: string) {
    setOwnedIds((prev) => new Set(prev).add(productId));
  }

  return (
    <div className="mx-auto max-w-6xl px-4 py-12">
      <h1 className="text-3xl font-bold tracking-tight">Store</h1>
      <p className="mt-2 max-w-2xl text-text-secondary">
        Digital products for competitive players and creators.
      </p>
      <p className="mt-4 max-w-2xl rounded-md border border-status-medium/40 bg-status-medium/10 px-4 py-3 text-xs text-status-medium">
        Test mode — purchases here are free while payments are being finalized. No real charge
        occurs.
      </p>

      {loading ? (
        <div className="mt-10 h-40" />
      ) : products.length === 0 ? (
        <p className="mt-10 text-text-secondary">No products available yet — check back soon.</p>
      ) : (
        <div className="mt-8 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {products.map((product) => (
            <ProductCard
              key={product.id}
              product={product}
              token={token}
              owned={ownedIds.has(product.id)}
              onPurchased={handlePurchased}
            />
          ))}
        </div>
      )}
    </div>
  );
}
