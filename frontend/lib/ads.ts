const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

/**
 * Set once the founder has real AdSense approval and a publisher ID
 * (see docs/decisions/05-founder-action-checklist.md) -- never a
 * placeholder value. No-ops entirely until then.
 */
export const ADSENSE_PUBLISHER_ID = process.env.NEXT_PUBLIC_ADSENSE_PUBLISHER_ID;

const DISPLAY_ADS_FLAG_KEY = "display-ads";

/**
 * A second, independent switch from ADSENSE_PUBLISHER_ID -- lets the
 * founder pull ads instantly from the admin panel's existing Feature
 * Flags screen (e.g. an AdSense policy issue) without a redeploy.
 * Cached briefly (not cache: "no-store") since a toggle doesn't need
 * to propagate within milliseconds, and every ad slot on every page
 * checks this.
 */
export async function isDisplayAdsEnabled(): Promise<boolean> {
  if (!ADSENSE_PUBLISHER_ID) return false;
  try {
    const res = await fetch(`${API_BASE_URL}/api/v1/feature-flags`, {
      next: { revalidate: 60 },
    });
    if (!res.ok) return false;
    const flags = (await res.json()) as string[];
    return flags.includes(DISPLAY_ADS_FLAG_KEY);
  } catch {
    return false;
  }
}
