import { buildMetadata } from "@/lib/seo";

export const metadata = buildMetadata({
  title: "Contact",
  description: "Get in touch with EXTREMIS Creator Hub.",
  path: "/contact",
});

export default function ContactPage() {
  return (
    <div className="mx-auto max-w-3xl px-4 py-12">
      <h1 className="text-3xl font-bold tracking-tight">Contact</h1>

      <div className="mt-6 flex flex-col gap-4 text-text-secondary">
        <p>
          For questions about a tool, a bug report, a takedown/privacy
          request, or anything else — email{" "}
          <a
            href="mailto:surya.chowdhury0412@gmail.com"
            className="text-brand-accent hover:underline"
          >
            surya.chowdhury0412@gmail.com
          </a>
          .
        </p>
        <p>
          This is a small, independently-run project — expect a response
          within a few days, not instantly.
        </p>
      </div>
    </div>
  );
}
