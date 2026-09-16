# EXTREMIS Creator Hub — Founder Action Checklist

## AI/CODE CAN BUILD

- Entire Next.js/TypeScript/Tailwind application
- All 5 MVP tool calculators/generators (deterministic, no AI cost)
- Database schema, migrations, seed data
- Authentication wiring (Auth.js integration code)
- Admin panel UI and logic
- Affiliate database structure, click logging, disclosure rendering
- Digital product purchase → secure delivery pipeline (code + logic)
- Payment provider abstraction/interface and webhook-handling code
- Premium/feature-flag/entitlement logic
- Transactional email templates and sending logic
- SEO implementation (sitemap, meta, structured data, on-page content
  structure)
- Analytics event-tracking code (the code that *sends* events)
- CI/CD pipeline, deployment configuration, environment variable
  templates
- All documentation files

## FOUNDER MUST DO MANUALLY

These require the founder's own identity, accounts, or judgment — the
AI will never request passwords, OTPs, recovery codes, or session
cookies for any of these:

- **Google Analytics:** create the GA4 property under the founder's
  own Google account; provide the resulting Measurement ID as an
  environment variable.
- **Google Search Console:** verify domain ownership (DNS TXT record
  or file upload) under the founder's account.
- **Google AdSense:** apply for an account once there is real original
  content and some traffic history; approval and timing are entirely
  Google's decision, not something the build can influence.
- **Google OAuth (if used for login):** create the OAuth client in
  Google Cloud Console under the founder's project; provide client
  ID/secret as environment variables.
- **Affiliate programs:** apply to each merchant/network individually
  (e.g. gear retailers); approval criteria and timelines are set by
  each program.
- **Payment provider onboarding:** founder chose PhonePe (₹0 setup/AMC,
  0% UPI transaction fee, ~2% on cards — best fit for a high-UPI-volume
  Indian store). Complete PhonePe's merchant onboarding/business
  verification, then provide the resulting client ID/secret/version and
  webhook username/password as environment variables (see
  `docs/ENV_VARS.md`). No live payment path is enabled until this is done.
- **Bank/payout configuration:** set up payout details with the chosen
  payment provider.
- **Domain purchase (optional, small cost):** the one part of this
  plan that is not strictly $0 if the founder wants a custom domain
  instead of a subdomain.
- **Legal/business compliance:** confirm any local business
  registration or tax obligations that apply once real revenue starts
  — outside the scope of what code can determine.
- **Digital product creation (initial content):** the actual template/
  overlay/preset files being sold are creative assets the founder (or
  a hired designer) produces; the platform only builds the
  infrastructure to sell and deliver them.
- **Strategic/brand decisions:** final call on pricing, which
  affiliate categories to pursue first, and how EXTREMIS Plays content
  cross-promotes the Hub.

For every item above, when the build reaches a point that depends on
it, the plan is: (1) build everything technically possible first, (2)
tell the founder exactly what to do, (3) stop at that
authorization/approval/payment step, (4) resume once the founder
supplies the resulting credentials via environment variables.
