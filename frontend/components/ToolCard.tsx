"use client";

import Link from "next/link";
import { useEffect, useState, type MouseEvent } from "react";
import { Card } from "./ui/Card";
import type { ToolSummary } from "@/lib/tools";
import { fetchCurrentUser } from "@/lib/api";
import { getToken } from "@/lib/auth";

export default function ToolCard({
  tool,
}: {
  tool: ToolSummary & { premiumOnly?: boolean };
}) {
  const [popupOpen, setPopupOpen] = useState(false);
  const [popupVisible, setPopupVisible] = useState(false);
  // Defaults to locked until the entitlement check resolves -- fail
  // safe, never briefly show an unlocked card to a non-entitled user.
  const [entitled, setEntitled] = useState(false);

  useEffect(() => {
    if (!tool.premiumOnly) return;
    const token = getToken();
    if (!token) return;
    fetchCurrentUser(token).then((user) => {
      if (user?.hasPremiumAccess) setEntitled(true);
    });
  }, [tool.premiumOnly]);

  const locked = Boolean(tool.premiumOnly) && !entitled;

  function openPopup() {
    setPopupOpen(true);
    requestAnimationFrame(() => setPopupVisible(true));
  }

  function closePopup() {
    setPopupVisible(false);
    setTimeout(() => setPopupOpen(false), 150);
  }

  function handleClick(e: MouseEvent<HTMLAnchorElement>) {
    if (locked) {
      e.preventDefault();
      openPopup();
    }
  }

  return (
    <>
      <Link href={`/tools/${tool.slug}`} onClick={handleClick} className="group block h-full">
        <Card className="flex h-full flex-col gap-3 transition-colors group-hover:border-border-strong">
          <div className="flex flex-wrap items-center gap-2">
            <span className="w-fit rounded-full border border-border-strong px-2 py-0.5 text-xs uppercase tracking-wide text-text-secondary">
              {tool.category}
            </span>
            {tool.premiumOnly && (
              <span className="flex w-fit items-center gap-1 rounded-full border border-status-medium/40 bg-status-medium/15 px-2 py-0.5 text-xs font-semibold uppercase tracking-wide text-status-medium">
                <span aria-hidden="true">⭐</span> Premium
              </span>
            )}
          </div>
          <h3 className="text-lg font-semibold text-text-primary">{tool.name}</h3>
          <p className="text-sm text-text-secondary">{tool.description}</p>
          <span className="mt-auto flex items-center gap-1.5 text-sm font-medium text-brand-accent group-hover:underline">
            {locked && <span aria-hidden="true">🔒</span>}
            Try it &rarr;
          </span>
        </Card>
      </Link>

      {popupOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4"
          onClick={closePopup}
        >
          <div
            className={`w-full max-w-sm rounded-lg border border-border-subtle bg-bg-surface p-6 text-center shadow-xl transition-all duration-150 ease-out ${
              popupVisible ? "scale-100 opacity-100" : "scale-95 opacity-0"
            }`}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="text-3xl">🔒</div>
            <p className="mt-2 text-lg font-semibold text-text-primary">Premium Tool</p>
            <p className="mt-1 text-sm text-text-secondary">Kindly contact Admin support</p>
            <button
              type="button"
              onClick={closePopup}
              className="mt-4 rounded-md border border-border-strong px-4 py-1.5 text-sm text-text-secondary hover:text-text-primary"
            >
              Close
            </button>
          </div>
        </div>
      )}
    </>
  );
}
