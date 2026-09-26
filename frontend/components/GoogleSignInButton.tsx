"use client";

import Script from "next/script";
import { useCallback, useRef } from "react";
import { signInWithGoogle } from "@/lib/api";
import { setToken } from "@/lib/auth";

const GOOGLE_CLIENT_ID = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;

type GoogleCredentialResponse = { credential: string };

interface GoogleIdentityServices {
  accounts: {
    id: {
      initialize(config: {
        client_id: string;
        callback: (response: GoogleCredentialResponse) => void;
      }): void;
      renderButton(parent: HTMLElement, options: { theme: string; size: string }): void;
    };
  };
}

declare global {
  interface Window {
    google?: GoogleIdentityServices;
  }
}

export default function GoogleSignInButton() {
  const buttonRef = useRef<HTMLDivElement>(null);

  const handleCredentialResponse = useCallback(async (response: GoogleCredentialResponse) => {
    try {
      const auth = await signInWithGoogle(response.credential);
      setToken(auth.token);
      // A real navigation, not router.push -- AuthNav (in the persistent
      // layout) only checks the token once on mount, so a client-side
      // route change left it stuck showing "Sign in" after a real login
      // (the header never remounted to re-check). A full navigation also
      // means landing on the home page, not the dashboard.
      window.location.href = "/";
    } catch {
      // Sign-in failure just leaves the user on the login page —
      // no account state changes without a successful response.
    }
  }, []);

  const initializeGoogleButton = useCallback(() => {
    if (!window.google || !buttonRef.current || !GOOGLE_CLIENT_ID) return;
    window.google.accounts.id.initialize({
      client_id: GOOGLE_CLIENT_ID,
      callback: handleCredentialResponse,
    });
    window.google.accounts.id.renderButton(buttonRef.current, {
      theme: "outline",
      size: "large",
    });
  }, [handleCredentialResponse]);

  if (!GOOGLE_CLIENT_ID) {
    return (
      <p className="rounded-md border border-border-subtle bg-bg-surface-alt px-4 py-3 text-sm text-text-muted">
        Google sign-in isn&apos;t configured yet.
      </p>
    );
  }

  return (
    <>
      <Script
        src="https://accounts.google.com/gsi/client"
        strategy="afterInteractive"
        onReady={initializeGoogleButton}
      />
      <div ref={buttonRef} />
    </>
  );
}
