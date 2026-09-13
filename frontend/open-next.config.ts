import { defineCloudflareConfig } from "@opennextjs/cloudflare";

// No R2 incremental-cache override -- the app doesn't rely on ISR
// (every server component fetch uses cache: "no-store"), so the
// default (in-memory, request-scoped) cache behavior is sufficient.
export default defineCloudflareConfig({});
