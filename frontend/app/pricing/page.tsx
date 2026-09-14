import Link from "next/link";
import { Card } from "@/components/ui/Card";
import { buildMetadata } from "@/lib/seo";
import { TOOLS } from "@/lib/tools";
import { fetchActiveProducts, fetchPublicTools } from "@/lib/api";

export const metadata = buildMetadata({
  title: "Pricing",
  description: "What's free, what's for sale, and how premium access works on EXTREMIS Creator Hub.",
  path: "/pricing",
});

function formatPrice(cents: number, currency: string) {
  if (cents === 0) return "Free";
  return `$${(cents / 100).toFixed(2)} ${currency}`;
}

export default async function PricingPage() {
  const [publicTools, products] = await Promise.all([fetchPublicTools(), fetchActiveProducts()]);
  const premiumBySlug = new Map(publicTools.map((t) => [t.slug, t.premiumOnly]));
  const premiumTools = TOOLS.filter((t) => premiumBySlug.get(t.slug));
  const freeTools = TOOLS.filter((t) => !premiumBySlug.get(t.slug));

  return (
    <div className="mx-auto max-w-5xl px-4 py-12">
      <h1 className="text-3xl font-bold tracking-tight">Pricing</h1>
      <p className="mt-2 max-w-2xl text-text-secondary">
        Most of this site is free, always. Here's exactly what costs what.
      </p>

      <div className="mt-8 grid gap-5 sm:grid-cols-3">
        <Card className="flex flex-col gap-3">
          <span className="w-fit rounded-full border border-status-high/40 bg-status-high/10 px-2 py-0.5 text-xs font-semibold uppercase tracking-wide text-status-high">
            Free
          </span>
          <p className="text-2xl font-bold text-text-primary">$0</p>
          <p className="text-sm text-text-secondary">No signup, no limits, no catch.</p>
          <ul className="mt-2 flex flex-col gap-1.5 text-sm text-text-secondary">
            {freeTools.map((tool) => (
              <li key={tool.slug}>✓ {tool.name}</li>
            ))}
          </ul>
          <Link
            href="/tools"
            className="mt-auto text-sm font-medium text-brand-accent hover:underline"
          >
            Browse all tools &rarr;
          </Link>
        </Card>

        <Card className="flex flex-col gap-3">
          <span className="w-fit rounded-full border border-border-strong px-2 py-0.5 text-xs font-semibold uppercase tracking-wide text-text-secondary">
            Digital Products
          </span>
          <p className="text-2xl font-bold text-text-primary">
            {products.length > 0 ? "One-time" : "Coming soon"}
          </p>
          <p className="text-sm text-text-secondary">
            Overlay packs, presets, and creator templates. Buy once, download anytime.
          </p>
          {products.length > 0 && (
            <ul className="mt-2 flex flex-col gap-1.5 text-sm text-text-secondary">
              {products.map((product) => (
                <li key={product.id} className="flex justify-between gap-2">
                  <span>{product.name}</span>
                  <span className="font-medium text-text-primary">
                    {formatPrice(product.priceCents, product.currency)}
                  </span>
                </li>
              ))}
            </ul>
          )}
          <Link href="/store" className="mt-auto text-sm font-medium text-brand-accent hover:underline">
            Visit the store &rarr;
          </Link>
        </Card>

        <Card className="flex flex-col gap-3">
          <span className="w-fit rounded-full border border-status-medium/40 bg-status-medium/10 px-2 py-0.5 text-xs font-semibold uppercase tracking-wide text-status-medium">
            ⭐ Premium
          </span>
          <p className="text-2xl font-bold text-text-primary">Invite-only</p>
          <p className="text-sm text-text-secondary">
            There's no self-serve checkout for premium yet — access is granted individually.
          </p>
          {premiumTools.length > 0 && (
            <ul className="mt-2 flex flex-col gap-1.5 text-sm text-text-secondary">
              {premiumTools.map((tool) => (
                <li key={tool.slug}>⭐ {tool.name}</li>
              ))}
            </ul>
          )}
          <Link href="/contact" className="mt-auto text-sm font-medium text-brand-accent hover:underline">
            Contact us for early access &rarr;
          </Link>
        </Card>
      </div>

      <p className="mt-10 text-center text-xs text-text-muted">
        Prices shown are live from the actual store — not placeholders.
      </p>
    </div>
  );
}
