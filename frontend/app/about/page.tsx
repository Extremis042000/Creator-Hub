import Link from "next/link";
import { buildMetadata } from "@/lib/seo";

export const metadata = buildMetadata({
  title: "About",
  description: "About EXTREMIS Creator Hub — free tools for competitive gaming players and creators.",
  path: "/about",
});

export default function AboutPage() {
  return (
    <div className="mx-auto max-w-3xl px-4 py-12">
      <h1 className="text-3xl font-bold tracking-tight">About</h1>

      <div className="mt-6 flex flex-col gap-4 text-text-secondary">
        <p>
          EXTREMIS Creator Hub is a set of free tools for competitive gaming
          players and gaming content creators — a KD ratio calculator,
          sensitivity converters for Valorant/CS2/CS:GO/Apex Legends and
          BGMI, and YouTube title/description generators. Every tool works
          instantly, with no signup required and no paywall on the core
          functionality.
        </p>
        <p>
          The Hub is built and run by <strong>EXTREMIS Plays</strong>, a
          gaming content creator brand. The tools grew out of a simple
          problem: the same sensitivity-conversion and content-prep tasks
          come up constantly while making gaming content, and most existing
          tools online are cluttered with ads or guess at their numbers
          instead of citing a source. Every conversion constant and
          recommendation range on this site is sourced and dated — see each
          tool's FAQ section for exactly where its numbers come from, and
          what confidence level they carry.
        </p>
        <p>
          Optional sign-in (via Google) unlocks saving and re-visiting your
          results, and a small store of digital creator products. The site
          is supported by affiliate links (clearly disclosed wherever they
          appear), a digital-product store, and — once approved — display
          advertising. None of that changes what the free tools do or cost.
        </p>
        <p>
          Questions, feedback, or a tool idea? See the{" "}
          <Link href="/contact" className="text-brand-accent hover:underline">
            Contact
          </Link>{" "}
          page.
        </p>
      </div>
    </div>
  );
}
