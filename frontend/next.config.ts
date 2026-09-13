import type { NextConfig } from "next";
import { initOpenNextCloudflareForDev } from "@opennextjs/cloudflare";

const nextConfig: NextConfig = {
  // Backend base URL, consumed by app/lib/api.ts. Set in .env.local
  // for local dev (see .env.local.example) and as a real env var at
  // build time for the Cloudflare Workers deploy (NEXT_PUBLIC_* vars
  // are inlined at build time, not runtime).
  env: {
    NEXT_PUBLIC_API_BASE_URL:
      process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080",
  },
};

export default nextConfig;

// Makes `next dev` behave consistently with the Cloudflare Workers
// runtime during local development.
initOpenNextCloudflareForDev();
