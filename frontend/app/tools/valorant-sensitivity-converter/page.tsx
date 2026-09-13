import Link from "next/link";
import AdSlot from "@/components/AdSlot";
import JsonLd from "@/components/JsonLd";
import SensitivityConverterForm from "@/components/tools/SensitivityConverterForm";
import PremiumToolGate from "@/components/tools/PremiumToolGate";
import { buildMetadata, faqJsonLd, toolJsonLd } from "@/lib/seo";
import { TOOLS } from "@/lib/tools";
import { fetchPublicTools } from "@/lib/api";

const TITLE = "Valorant Sensitivity Converter";
const DESCRIPTION =
  "Convert your mouse sensitivity between Valorant, CS2, CS:GO, and Apex Legends while preserving the same physical aim feel. Free, no signup.";
const PATH = "/tools/valorant-sensitivity-converter";

export const metadata = buildMetadata({ title: TITLE, description: DESCRIPTION, path: PATH });

const FAQS = [
  {
    q: "How does the conversion work?",
    a: "We convert your sensitivity into a physical distance (cm per 360° turn) using each game's yaw constant, then calculate the sensitivity in your target game that reproduces the same physical distance.",
  },
  {
    q: "Why isn't my target game listed?",
    a: "We only support conversions between games whose sensitivity constants we've verified with real sources. See our data verification report for exactly what's supported and why.",
  },
  {
    q: "What if I convert between the same game?",
    a: "Your sensitivity is returned unchanged — there's nothing to convert.",
  },
];

export default async function SensitivityConverterPage() {
  const relatedTools = TOOLS.filter((t) => t.slug !== "valorant-sensitivity-converter").slice(0, 3);
  const publicTools = await fetchPublicTools();
  const premiumOnly = publicTools.find((t) => t.slug === "valorant-sensitivity-converter")?.premiumOnly ?? false;

  return (
    <div className="mx-auto max-w-4xl px-4 py-12">
      <JsonLd data={toolJsonLd({ name: TITLE, description: DESCRIPTION, path: PATH })} />
      <JsonLd data={faqJsonLd(FAQS)} />
      <p className="text-sm text-text-secondary">
        <Link href="/tools" className="hover:text-text-primary">
          Tools
        </Link>{" "}
        / Valorant Sensitivity Converter
      </p>
      <h1 className="mt-2 text-3xl font-bold tracking-tight">Valorant Sensitivity Converter</h1>
      <p className="mt-2 text-text-secondary">
        Convert your sensitivity between games while keeping the same physical aim feel.
      </p>

      <div className="mt-8">
        <PremiumToolGate premiumOnly={premiumOnly}>
          <SensitivityConverterForm />
        </PremiumToolGate>
      </div>

      <section className="mt-16">
        <h2 className="text-xl font-semibold">How to use this tool</h2>
        <ol className="mt-3 list-decimal space-y-2 pl-5 text-text-secondary">
          <li>Select the game you're converting from and to.</li>
          <li>Enter your mouse DPI.</li>
          <li>Enter your current in-game sensitivity.</li>
          <li>Click Convert to see your equivalent sensitivity in the target game.</li>
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
