import { buildMetadata } from "@/lib/seo";

export const metadata = buildMetadata({
  title: "Terms of Service",
  description: "The terms for using EXTREMIS Creator Hub.",
  path: "/terms",
});

const LAST_UPDATED = "September 14, 2026";

export default function TermsPage() {
  return (
    <div className="mx-auto max-w-3xl px-4 py-12">
      <h1 className="text-3xl font-bold tracking-tight">Terms of Service</h1>
      <p className="mt-2 text-sm text-text-muted">Last updated: {LAST_UPDATED}</p>

      <div className="mt-8 flex flex-col gap-8 text-text-secondary">
        <section>
          <h2 className="text-lg font-semibold text-text-primary">Using the site</h2>
          <p className="mt-2">
            The free tools on this site (KD calculator, sensitivity
            converters, title/description generators) are provided for
            personal, non-commercial use, at no cost, with no account
            required. By using this site you agree to these terms.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-text-primary">Accuracy of tool results</h2>
          <p className="mt-2">
            Sensitivity-conversion constants and recommendation ranges are
            sourced from public community data, cited and dated on each
            tool's page — they are starting points, not guarantees, and are
            not official values from any game's developer unless explicitly
            stated. Performance categories (e.g. KD ratio bands) are general
            community benchmarks, not an official ranking. We do our best to
            keep sourcing honest and up to date, but we make no warranty
            that any result is complete, current, or fits your specific
            situation.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-text-primary">Accounts</h2>
          <p className="mt-2">
            Signing in (via Google) is optional and only required to save
            results, make purchases, or access premium features. You're
            responsible for keeping access to your Google account secure —
            we have no visibility into or control over your Google
            credentials. You can delete your account at any time from your
            dashboard.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-text-primary">Purchases</h2>
          <p className="mt-2">
            Digital products are delivered electronically upon a completed
            purchase. Because delivery is instant and the product is
            non-physical, purchases are generally non-refundable once
            delivered, except where required by law. If something's
            actually broken (a bad download link, a wrong file), contact us
            — we'll fix it.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-text-primary">Affiliate links</h2>
          <p className="mt-2">
            Some pages contain affiliate links, clearly disclosed where they
            appear. We may earn a commission if you buy through one, at no
            extra cost to you. We only recommend products we believe are
            genuinely good — an affiliate relationship does not change that
            judgment.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-text-primary">Acceptable use</h2>
          <p className="mt-2">
            Don't attempt to abuse, scrape at scale, disrupt, or gain
            unauthorized access to this site or its backend systems. We may
            suspend or terminate access for anyone who does.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-text-primary">Changes</h2>
          <p className="mt-2">
            We may update these terms as the site changes. Continuing to use
            the site after an update means you accept the revised terms.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-text-primary">Contact</h2>
          <p className="mt-2">
            Questions about these terms:{" "}
            <a
              href="mailto:surya.chowdhury0412@gmail.com"
              className="text-brand-accent hover:underline"
            >
              surya.chowdhury0412@gmail.com
            </a>
            .
          </p>
        </section>

        <p className="rounded-md border border-border-subtle bg-bg-surface px-4 py-3 text-xs text-text-muted">
          These terms describe our actual practices as accurately as we
          can, but are not a substitute for professional legal advice —
          in particular, no specific governing jurisdiction is named here
          yet. Consult a lawyer before relying on this page for legal
          enforceability.
        </p>
      </div>
    </div>
  );
}
