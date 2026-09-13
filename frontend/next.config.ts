import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Backend base URL, consumed by app/lib/api.ts. Set in .env.local
  // for local dev (see .env.local.example) and as a real env var in
  // production once the backend is deployed (Phase 22).
  env: {
    NEXT_PUBLIC_API_BASE_URL:
      process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080",
  },
};

export default nextConfig;
