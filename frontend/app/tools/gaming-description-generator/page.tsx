import Link from "next/link";
import AdSlot from "@/components/AdSlot";
import JsonLd from "@/components/JsonLd";
import DescriptionGeneratorForm from "@/components/tools/DescriptionGeneratorForm";
import PremiumToolGate from "@/components/tools/PremiumToolGate";
import { buildMetadata, faqJsonLd, toolJsonLd } from "@/lib/seo";
import { TOOLS } from "@/lib/tools";
import { fetchPublicTools } from "@/lib/api";

const TITLE = "Gaming YouTube Description Generator";
const DESCRIPTION =
  "Build a structured, SEO-friendly YouTube description with hashtags and social links in seconds. Free, no signup.";
const PATH = "/tools/gaming-description-generator";

export const metadata = buildMetadata({ title: TITLE, description: DESCRIPTION, path: PATH });

const FAQS = [
  {
    q: "Does this use AI?",
    a: "Free descriptions come from a template engine, so results are instant and predictable. Signed-in premium accounts get an AI-written description instead — and it automatically falls back to templates if AI is ever unavailable, so the tool never breaks.",
  },
  {
    q: "What gets included in the description?",
    a: "An intro paragraph, your social links (if provided), your keywords, and relevant hashtags — all structured for YouTube's description box.",
  },
  {
    q: "How are hashtags generated?",
    a: "From your game, topic, and keywords — deduplicated and capped at 15, matching YouTube's own hashtag guidance.",
  },
  {
    q: "Do social links need to be HTTPS?",
    a: "Yes — we validate that any link you add is a proper https:// URL before generating your description.",
  },
];

export default async function DescriptionGeneratorPage() {
  const relatedTools = TOOLS.filter((t) => t.slug !== "gaming-description-generator").slice(0, 3);
  const publicTools = await fetchPublicTools();
  const premiumOnly = publicTools.find((t) => t.slug === "gaming-description-generator")?.premiumOnly ?? false;

  return (
    <div className="mx-auto max-w-4xl px-4 py-12">
      <JsonLd data={toolJsonLd({ name: TITLE, description: DESCRIPTION, path: PATH })} />
      <JsonLd data={faqJsonLd(FAQS)} />
      <p className="text-sm text-text-secondary">
        <Link href="/tools" className="hover:text-text-primary">
          Tools
        </Link>{" "}
        / Gaming YouTube Description Generator
      </p>
      <h1 className="mt-2 text-3xl font-bold tracking-tight">
        Gaming YouTube Description Generator
      </h1>
      <p className="mt-2 text-text-secondary">
        Build a structured, SEO-friendly description with hashtags in seconds.
      </p>

      <div className="mt-8">
        <PremiumToolGate premiumOnly={premiumOnly}>
          <DescriptionGeneratorForm />
        </PremiumToolGate>
      </div>

      <section className="mt-16">
        <h2 className="text-xl font-semibold">How to use this tool</h2>
        <ol className="mt-3 list-decimal space-y-2 pl-5 text-text-secondary">
          <li>Enter the game, video topic, and your channel name.</li>
          <li>Optionally add keywords and a social link.</li>
          <li>Click Generate description and copy the result.</li>
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
