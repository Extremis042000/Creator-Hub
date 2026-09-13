import Script from "next/script";
import { ADSENSE_PUBLISHER_ID, isDisplayAdsEnabled } from "@/lib/ads";

/** Renders nothing until AdSense is approved (publisher ID set) AND the "display-ads" flag is enabled. */
export default async function AdSenseScript() {
  if (!(await isDisplayAdsEnabled())) return null;

  return (
    <Script
      async
      src={`https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=${ADSENSE_PUBLISHER_ID}`}
      crossOrigin="anonymous"
      strategy="afterInteractive"
    />
  );
}
