import Link from "next/link";
import AdSlot from "@/components/AdSlot";
import JsonLd from "@/components/JsonLd";
import BgmiSensitivityForm from "@/components/tools/BgmiSensitivityForm";
import { buildMetadata, faqJsonLd, toolJsonLd } from "@/lib/seo";
import { TOOLS } from "@/lib/tools";

const TITLE = "BGMI Sensitivity Helper";
const DESCRIPTION =
  "Starting-point gyroscope and ADS sensitivity ranges for BGMI, based on real community data. Free, no signup.";
const PATH = "/tools/bgmi-sensitivity-helper";

export const metadata = buildMetadata({ title: TITLE, description: DESCRIPTION, path: PATH });

const FAQS = [
  {
    q: "Are these the \"correct\" settings?",
    a: "No — they're evidence-based starting points, not a guarantee of better performance. Personal comfort and device performance matter too.",
  },
  {
    q: "Why does confidence vary by row?",
    a: "Some ranges are backed by multiple independent sources (Medium confidence); others rest on a single well-documented source (Low confidence). We show this honestly instead of pretending every number is equally certain.",
  },
  {
    q: "What does \"not enough data yet\" mean?",
    a: "For a few scope/gyroscope combinations, we haven't found evidence we're confident enough to publish a range for — we say so rather than guessing.",
  },
];

export default function BgmiSensitivityPage() {
  const relatedTools = TOOLS.filter((t) => t.slug !== "bgmi-sensitivity-helper").slice(0, 3);

  return (
    <div className="mx-auto max-w-4xl px-4 py-12">
      <JsonLd data={toolJsonLd({ name: TITLE, description: DESCRIPTION, path: PATH })} />
      <JsonLd data={faqJsonLd(FAQS)} />
      <p className="text-sm text-text-secondary">
        <Link href="/tools" className="hover:text-text-primary">
          Tools
        </Link>{" "}
        / BGMI Sensitivity Helper
      </p>
      <h1 className="mt-2 text-3xl font-bold tracking-tight">BGMI Sensitivity Helper</h1>
      <p className="mt-2 text-text-secondary">
        Get starting-point sensitivity ranges for every scope level, based on real data.
      </p>

      <div className="mt-8">
        <BgmiSensitivityForm />
      </div>

      <section className="mt-16">
        <h2 className="text-xl font-semibold">How to use this tool</h2>
        <ol className="mt-3 list-decimal space-y-2 pl-5 text-text-secondary">
          <li>Tell us whether you play with gyroscope on or off.</li>
          <li>Click Get recommendations to see a range for every scope level.</li>
          <li>Start in the middle of each range and adjust to your own comfort.</li>
        </ol>
      </section>

      <section className="mt-12">
        <h2 className="text-xl font-semibold">FAQ</h2>
        <div className="mt-3 flex flex-col gap-4">
          {FAQS.map((faq) => (
            <div key={faq.q}>
              <h3 className="font-medium text-text-primary">{faq.q}</h3>
              <p className="mt-1 text-sm text-text-secondary">{faq.a}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="mt-12">
        <h2 className="text-xl font-semibold">Related tools</h2>
        <div className="mt-3 flex flex-wrap gap-3">
          {relatedTools.map((tool) => (
            <Link
              key={tool.slug}
              href={`/tools/${tool.slug}`}
              className="rounded-md border border-border-subtle px-3 py-2 text-sm text-text-secondary hover:border-border-strong hover:text-text-primary"
            >
              {tool.name}
            </Link>
          ))}
        </div>
      </section>

      <AdSlot slotId="tool-page-bottom" />
    </div>
  );
}
