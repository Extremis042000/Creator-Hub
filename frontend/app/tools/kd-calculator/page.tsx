import Link from "next/link";
import AdSlot from "@/components/AdSlot";
import JsonLd from "@/components/JsonLd";
import KdCalculatorForm from "@/components/tools/KdCalculatorForm";
import PremiumToolGate from "@/components/tools/PremiumToolGate";
import { buildMetadata, faqJsonLd, toolJsonLd } from "@/lib/seo";
import { TOOLS } from "@/lib/tools";
import { fetchPublicTools } from "@/lib/api";

const TITLE = "KD Ratio Calculator";
const DESCRIPTION =
  "Instantly calculate your kill/death ratio and see your performance category. Free, no signup required.";
const PATH = "/tools/kd-calculator";

export const metadata = buildMetadata({ title: TITLE, description: DESCRIPTION, path: PATH });

const FAQS = [
  {
    q: "How is KD ratio calculated?",
    a: "KD ratio is your total kills divided by your total deaths, rounded to two decimal places. For example, 20 kills and 10 deaths gives a 2.00 KD ratio.",
  },
  {
    q: "What happens if I have zero deaths?",
    a: "A KD ratio isn't mathematically defined when deaths is zero — we show \"Perfect / No Deaths\" instead of a misleading number.",
  },
  {
    q: "What do the performance categories mean?",
    a: "They're general bands (Developing, Solid, Strong, Elite) based on common community benchmarks — not an official ranking or a guarantee of skill.",
  },
  {
    q: "Is my data saved?",
    a: "Only if you click \"Get share link\" — otherwise nothing is stored, and the calculation never touches our database.",
  },
];

export default async function KdCalculatorPage() {
  const relatedTools = TOOLS.filter((t) => t.slug !== "kd-calculator").slice(0, 3);
  const publicTools = await fetchPublicTools();
  const premiumOnly = publicTools.find((t) => t.slug === "kd-calculator")?.premiumOnly ?? false;

  return (
    <div className="mx-auto max-w-4xl px-4 py-12">
      <JsonLd data={toolJsonLd({ name: TITLE, description: DESCRIPTION, path: PATH })} />
      <JsonLd data={faqJsonLd(FAQS)} />
      <p className="text-sm text-text-secondary">
        <Link href="/tools" className="hover:text-text-primary">
          Tools
        </Link>{" "}
        / KD Ratio Calculator
      </p>
      <h1 className="mt-2 text-3xl font-bold tracking-tight">KD Ratio Calculator</h1>
      <p className="mt-2 text-text-secondary">
        Enter your kills and deaths to instantly calculate your KD ratio.
      </p>

      <div className="mt-8">
        <PremiumToolGate premiumOnly={premiumOnly}>
          <KdCalculatorForm />
        </PremiumToolGate>
      </div>

      <section className="mt-16">
        <h2 className="text-xl font-semibold">How to use this tool</h2>
        <ol className="mt-3 list-decimal space-y-2 pl-5 text-text-secondary">
          <li>Enter your total kills from a match or session.</li>
          <li>Enter your total deaths for the same match or session.</li>
          <li>Click Calculate to see your KD ratio and performance category.</li>
          <li>Optionally get a shareable link to send to friends or teammates.</li>
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
