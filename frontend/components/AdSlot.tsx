import { ADSENSE_PUBLISHER_ID, isDisplayAdsEnabled } from "@/lib/ads";

/**
 * Renders nothing (zero DOM footprint, zero Core Web Vitals impact)
 * until ads are actually live -- see lib/ads.ts. `slotId` is a
 * placeholder string; once real AdSense ad units exist, replace it
 * with the real numeric ad-slot ID from the AdSense dashboard for
 * this specific placement.
 */
export default async function AdSlot({ slotId }: { slotId: string }) {
  if (!(await isDisplayAdsEnabled())) return null;

  return (
    <div className="my-8 flex justify-center">
      <ins
        className="adsbygoogle"
        style={{ display: "block", minHeight: 250 }}
        data-ad-client={ADSENSE_PUBLISHER_ID}
        data-ad-slot={slotId}
        data-ad-format="auto"
        data-full-width-responsive="true"
      />
      <script
        // Required per-unit AdSense activation call -- safe as static
        // markup since this component has no client interactivity.
        dangerouslySetInnerHTML={{ __html: "(adsbygoogle = window.adsbygoogle || []).push({});" }}
      />
    </div>
  );
}
