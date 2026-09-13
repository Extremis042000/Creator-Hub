import AffiliateProductCard from "@/components/AffiliateProductCard";
import { buildMetadata } from "@/lib/seo";
import { fetchActiveAffiliateProducts } from "@/lib/api";

export const metadata = buildMetadata({
  title: "Recommended Gear",
  description:
    "Gear recommendations for competitive gaming and content creation, hand-picked by EXTREMIS Plays.",
  path: "/gear",
});

export default async function GearPage() {
  const products = await fetchActiveAffiliateProducts();

  return (
    <div className="mx-auto max-w-6xl px-4 py-12">
      <h1 className="text-3xl font-bold tracking-tight">Recommended Gear</h1>
      <p className="mt-2 max-w-2xl text-text-secondary">
        Gear we actually recommend for competitive play and content creation.
      </p>
      <p className="mt-4 max-w-2xl rounded-md border border-border-subtle bg-bg-surface px-4 py-3 text-xs text-text-muted">
        Disclosure: some links on this page are affiliate links. If you buy through
        them, EXTREMIS Creator Hub may earn a small commission at no extra cost to
        you. We only recommend gear we believe is genuinely good.
      </p>

      {products.length === 0 ? (
        <p className="mt-10 text-text-secondary">No gear recommendations yet — check back soon.</p>
      ) : (
        <div className="mt-8 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {products.map((product) => (
            <AffiliateProductCard key={product.id} product={product} />
          ))}
        </div>
      )}
    </div>
  );
}
