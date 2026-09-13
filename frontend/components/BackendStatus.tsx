"use client";

import { useEffect, useState } from "react";
import { fetchBackendHealth, type BackendHealth } from "@/lib/api";

/**
 * Temporary Phase 4 ("project init") connectivity check — confirms
 * the frontend can reach the backend's health endpoint. Not a
 * user-facing feature; safe to remove once Phase 5+ integration work
 * makes it redundant, or keep as a quiet footer diagnostic.
 */
export default function BackendStatus() {
  const [health, setHealth] = useState<BackendHealth | null>(null);

  useEffect(() => {
    fetchBackendHealth().then(setHealth);
  }, []);

  if (!health) return null;

  const color =
    health.status === "UP"
      ? "text-status-high"
      : health.status === "DOWN"
        ? "text-status-medium"
        : "text-text-muted";

  return (
    <p className="font-mono text-xs text-text-muted">
      Backend:{" "}
      <span className={color}>
        {health.status === "UP" ? "connected" : health.status.toLowerCase()}
      </span>
    </p>
  );
}
