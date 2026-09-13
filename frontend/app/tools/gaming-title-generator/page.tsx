import Link from "next/link";
import JsonLd from "@/components/JsonLd";
import TitleGeneratorForm from "@/components/tools/TitleGeneratorForm";
import { buildMetadata, faqJsonLd, toolJsonLd } from "@/lib/seo";
import { TOOLS } from "@/lib/tools";

const TITLE = "Gaming YouTube Title Generator";
const DESCRIPTION =
  "Generate clickable, non-misleading YouTube title ideas for your gaming videos. Free, no signup, no AI cost.";
const PATH = "/tools/gaming-title-generator";

export const metadata = buildMetadata({ title: TITLE, description: DESCRIPTION, path: PATH });

const FAQS = [
  {
    q: "Does this use AI?",
    a: "No — titles are generated from templates, not AI, so results are instant, free, and predictable.",
  },
  {
    q: "Will titles be misleading or clickbait?",
    a: "No — we deliberately avoid unsubstantiated absolute claims like \"world record\" or \"best ever\" in every template.",
  },
  {
    q: "Can I add my own keywords?",
    a: "Yes — add up to 10 comma-separated keywords and we'll work them into extra title options.",
  },
];

export default function TitleGeneratorPage() {
  const relatedTools = TOOLS.filter((t) => t.slug !== "gaming-title-generator").slice(0, 3);

  return (
    <div className="mx-auto max-w-4xl px-4 py-12">
      <JsonLd data={toolJsonLd({ name: TITLE, description: DESCRIPTION, path: PATH })} />
      <JsonLd data={faqJsonLd(FAQS)} />
      <p className="text-sm text-text-secondary">
        <Link href="/tools" className="hover:text-text-primary">
          Tools
        </Link>{" "}
        / Gaming YouTube Title Generator
      </p>
      <h1 className="mt-2 text-3xl font-bold tracking-tight">Gaming YouTube Title Generator</h1>
      <p className="mt-2 text-text-secondary">
        Get clickable, non-misleading title ideas for your next gaming video.
      </p>

      <div className="mt-8">
        <TitleGeneratorForm />
      </div>

      <section className="mt-16">
        <h2 className="text-xl font-semibold">How to use this tool</h2>
        <ol className="mt-3 list-decimal space-y-2 pl-5 text-text-secondary">
          <li>Enter the game and a short description of your video's topic.</li>
          <li>Pick the video type and tone that best fits.</li>
          <li>Optionally add a few keywords.</li>
          <li>Click Generate titles and copy your favorite.</li>
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
    </div>
  );
}
