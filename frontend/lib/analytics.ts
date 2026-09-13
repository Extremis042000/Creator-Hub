export const GA_MEASUREMENT_ID = process.env.NEXT_PUBLIC_GA_MEASUREMENT_ID;

declare global {
  interface Window {
    gtag?: (...args: unknown[]) => void;
  }
}

/**
 * No-ops entirely when NEXT_PUBLIC_GA_MEASUREMENT_ID isn't set —
 * never sends events to a placeholder/fake property. The founder
 * creates the real GA4 property themselves (Founder Action
 * Checklist) and supplies the ID via env var; this code activates
 * automatically once that's set, no code change needed.
 */
export function trackEvent(eventName: string, params?: Record<string, unknown>) {
  if (!GA_MEASUREMENT_ID) return;
  if (typeof window === "undefined" || typeof window.gtag !== "function") return;
  window.gtag("event", eventName, params);
}
