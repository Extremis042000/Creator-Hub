import type { MetadataRoute } from "next";
import { SITE_URL } from "@/lib/seo";
import { TOOLS } from "@/lib/tools";

/**
 * Only lists pages that actually exist with real content — no /about,
 * /privacy, /terms, /contact yet (those 404 today; adding them here
 * would submit broken URLs to search engines). Update this list as
 * each page ships.
 */
export default function sitemap(): MetadataRoute.Sitemap {
  const now = new Date();

  const staticPages: MetadataRoute.Sitemap = [
    { url: SITE_URL, lastModified: now, changeFrequency: "weekly", priority: 1 },
    { url: `${SITE_URL}/tools`, lastModified: now, changeFrequency: "weekly", priority: 0.9 },
  ];

  const toolPages: MetadataRoute.Sitemap = TOOLS.map((tool) => ({
    url: `${SITE_URL}/tools/${tool.slug}`,
    lastModified: now,
    changeFrequency: "monthly",
    priority: 0.8,
  }));

  return [...staticPages, ...toolPages];
}
