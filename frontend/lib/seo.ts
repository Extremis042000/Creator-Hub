import type { Metadata } from "next";

export const SITE_NAME = "EXTREMIS Creator Hub";
export const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL ?? "https://extremiscreatorhub.com";

/**
 * Consistent title/description/canonical/OG/Twitter metadata for a
 * page. Every tool page and the homepage/tools-directory use this so
 * OG/Twitter cards are never silently missing — see
 * docs/03-development-roadmap.md Phase 14.
 */
export function buildMetadata({
  title,
  description,
  path,
}: {
  title: string;
  description: string;
  path: string;
}): Metadata {
  const url = `${SITE_URL}${path}`;
  return {
    title,
    description,
    alternates: { canonical: url },
    openGraph: {
      title,
      description,
      url,
      siteName: SITE_NAME,
      type: "website",
    },
    twitter: {
      card: "summary_large_image",
      title,
      description,
    },
  };
}

/** FAQPage JSON-LD — only used on pages with real, original FAQ content. */
export function faqJsonLd(faqs: { q: string; a: string }[]) {
  return {
    "@context": "https://schema.org",
    "@type": "FAQPage",
    mainEntity: faqs.map((faq) => ({
      "@type": "Question",
      name: faq.q,
      acceptedAnswer: {
        "@type": "Answer",
        text: faq.a,
      },
    })),
  };
}

/** SoftwareApplication JSON-LD for a free browser tool page. */
export function toolJsonLd({ name, description, path }: { name: string; description: string; path: string }) {
  return {
    "@context": "https://schema.org",
    "@type": "SoftwareApplication",
    name,
    description,
    url: `${SITE_URL}${path}`,
    applicationCategory: "UtilityApplication",
    operatingSystem: "Any (web browser)",
    offers: {
      "@type": "Offer",
      price: "0",
      priceCurrency: "USD",
    },
  };
}
