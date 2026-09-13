export type ToolSummary = {
  slug: string;
  name: string;
  description: string;
  category: "competitive" | "creator";
};

/**
 * Static for now — matches the 5 MVP tools from docs/06-prd.md.
 * Once the admin panel exists (Phase 17), this becomes a build-time
 * fetch from the real Tool table instead of a hardcoded list; the
 * shape stays the same so pages don't need to change.
 */
export const TOOLS: ToolSummary[] = [
  {
    slug: "valorant-sensitivity-converter",
    name: "Valorant Sensitivity Converter",
    description: "Convert your sensitivity between Valorant, CS2, CS:GO, and more.",
    category: "competitive",
  },
  {
    slug: "bgmi-sensitivity-helper",
    name: "BGMI Sensitivity Helper",
    description: "Starting-point gyroscope and ADS sensitivity ranges for your play style.",
    category: "competitive",
  },
  {
    slug: "kd-calculator",
    name: "KD Ratio Calculator",
    description: "Instantly calculate your kill/death ratio and performance category.",
    category: "competitive",
  },
  {
    slug: "gaming-title-generator",
    name: "Gaming YouTube Title Generator",
    description: "Generate clickable, non-misleading title ideas for your gaming videos.",
    category: "creator",
  },
  {
    slug: "gaming-description-generator",
    name: "Gaming YouTube Description Generator",
    description: "Build a structured, SEO-friendly description with hashtags in seconds.",
    category: "creator",
  },
];
