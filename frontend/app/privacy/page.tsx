import { buildMetadata } from "@/lib/seo";

export const metadata = buildMetadata({
  title: "Privacy Policy",
  description: "How EXTREMIS Creator Hub collects, uses, and protects your data.",
  path: "/privacy",
});

const LAST_UPDATED = "September 22, 2026";

export default function PrivacyPage() {
  return (
    <div className="mx-auto max-w-3xl px-4 py-12">
      <h1 className="text-3xl font-bold tracking-tight">Privacy Policy</h1>
      <p className="mt-2 text-sm text-text-muted">Last updated: {LAST_UPDATED}</p>

      <div className="mt-8 flex flex-col gap-8 text-text-secondary">
        <section>
          <h2 className="text-lg font-semibold text-text-primary">Using the tools without an account</h2>
          <p className="mt-2">
            Every tool on this site works fully without signing in. Anonymous
            tool use does not create any account record — nothing links a
            calculation you run to an identity unless you explicitly choose
            to save it (see "Getting a share link" below).
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-text-primary">Information we collect</h2>
          <ul className="mt-2 list-disc space-y-2 pl-5">
            <li>
              <strong>If you sign in:</strong> we use Google Sign-In. We
              receive your email address and display name from Google — we
              never see or store your Google password. We create an account
              record tied to your Google identity so you can access saved
              results, purchases, and premium features.
            </li>
            <li>
              <strong>Getting a share link:</strong> if you choose to save a
              tool result for sharing, the input and output of that
              calculation are stored, tied to your account if you're signed
              in (or anonymously, expiring after 30 days, if not).
            </li>
            <li>
              <strong>Digital product purchases:</strong> if you buy a
              digital product, we record the order, its status, and (once
              real payments are enabled) payment confirmation details from
              our payment provider. We never see or store your card details
              directly — those are handled entirely by the payment provider.
            </li>
            <li>
              <strong>Affiliate link clicks:</strong> when you click a gear
              recommendation, we log that a click happened (and your account,
              if you're signed in) before redirecting you to the merchant's
              site, so we can measure whether recommendations are useful.
            </li>
          </ul>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-text-primary">Smarter results from AI-assisted tools</h2>
          <p className="mt-2">
            To give you sharper, more relevant suggestions, the{" "}
            <strong>Gaming YouTube Title Generator</strong> and{" "}
            <strong>Gaming YouTube Description Generator</strong> can use an AI
            language model to generate your result, instead of only picking from
            a fixed set of templates. We're planning to bring the same
            AI-assisted approach to more tools over time, including an upcoming{" "}
            <strong>AI Thumbnail Generator</strong>.
          </p>
          <ul className="mt-2 list-disc space-y-2 pl-5">
            <li>
              Only the details you type into that specific tool (e.g. the game,
              topic, tone, and any keywords) are sent for generation — never
              your account, email, or any other identifying information.
            </li>
            <li>
              Generation is handled by one or more third-party AI service
              providers we've vetted for this purpose. Which provider is active
              can change as we improve reliability and quality — this policy
              covers that in general rather than naming one permanently.
            </li>
            <li>
              If AI generation is ever unavailable, the tool automatically
              falls back to its original template engine, so you always get a
              usable result — never an error instead of your title or
              description.
            </li>
          </ul>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-text-primary">Cookies and third-party services</h2>
          <p className="mt-2">
            Signing in does not use a tracking cookie — your session is kept
            in your browser's local storage, sent only to this site's own
            servers. Two third-party services are built into this site but
            are only active when explicitly turned on:
          </p>
          <ul className="mt-2 list-disc space-y-2 pl-5">
            <li>
              <strong>Google Analytics</strong> — if enabled, collects
              standard usage analytics (pages visited, general location,
              device type) under Google's own privacy practices.
            </li>
            <li>
              <strong>Google AdSense</strong> — if enabled, may use cookies
              to serve and measure ads, under Google's own ad policies. You
              can control ad personalization through{" "}
              <a
                href="https://adssettings.google.com"
                className="text-brand-accent hover:underline"
                target="_blank"
                rel="noreferrer"
              >
                Google's Ads Settings
              </a>
              .
            </li>
          </ul>
          <p className="mt-2">
            Neither is active unless clearly turned on — if you don't see ads
            on the site, analytics/ads scripts are not currently loaded for
            you.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-text-primary">Where data is stored</h2>
          <p className="mt-2">
            Account and application data is stored in a managed Postgres
            database (Neon). Error reports (if an unexpected error occurs)
            may be sent to our error-tracking provider (Sentry) — we do not
            configure this to include your IP address or request headers.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-text-primary">Your choices</h2>
          <ul className="mt-2 list-disc space-y-2 pl-5">
            <li>You can delete your account at any time from your dashboard.</li>
            <li>
              You can request a copy of, or the deletion of, any data we
              hold about you by emailing the address below.
            </li>
          </ul>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-text-primary">Children's privacy</h2>
          <p className="mt-2">
            This site is not directed at children under 13, and we do not
            knowingly collect data from anyone under that age.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-text-primary">Changes to this policy</h2>
          <p className="mt-2">
            We may update this policy as the site changes. Material changes
            will update the "Last updated" date above.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold text-text-primary">Contact</h2>
          <p className="mt-2">
            Questions about this policy, or a data request:{" "}
            <a
              href="mailto:surya.chowdhury0412@gmail.com"
              className="text-brand-accent hover:underline"
            >
              surya.chowdhury0412@gmail.com
            </a>
            .
          </p>
        </section>

        <p className="rounded-md border border-border-subtle bg-bg-surface px-4 py-3 text-xs text-text-muted">
          This policy describes our actual data practices as accurately as
          we can, but it is not a substitute for professional legal advice.
          If your jurisdiction has specific requirements (GDPR, CCPA, or
          similar), consult a lawyer before relying on this page for legal
          compliance.
        </p>
      </div>
    </div>
  );
}
