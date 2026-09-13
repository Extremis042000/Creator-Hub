"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import {
  deleteAccount,
  fetchActiveProducts,
  fetchCurrentUser,
  fetchOwnedProductIds,
  fetchProductDownloadUrl,
  ApiError,
  type CurrentUser,
  type PublicProduct,
} from "@/lib/api";
import { clearToken, getToken } from "@/lib/auth";

function OwnedProductRow({ product, token }: { product: PublicProduct; token: string }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleDownload() {
    setBusy(true);
    setError(null);
    try {
      const { downloadUrl } = await fetchProductDownloadUrl(token, product.id);
      window.open(`${process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080"}${downloadUrl}`, "_blank");
    } catch (err) {
      setError(err instanceof ApiError ? err.body.message : "Couldn't get the download link.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex items-center justify-between gap-3 border-b border-border-subtle/50 py-2 last:border-b-0">
      <span className="text-sm text-text-primary">{product.name}</span>
      <div className="flex items-center gap-2">
        {error && <span className="text-xs text-brand-primary">{error}</span>}
        <Button type="button" onClick={handleDownload} disabled={busy} className="text-xs">
          {busy ? "Preparing..." : "Download"}
        </Button>
      </div>
    </div>
  );
}

export default function DashboardPage() {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [deleting, setDeleting] = useState(false);
  const [ownedProducts, setOwnedProducts] = useState<PublicProduct[]>([]);
  const [token, setToken] = useState<string | null>(null);
  const router = useRouter();

  useEffect(() => {
    const token = getToken();
    if (!token) {
      router.replace("/login");
      return;
    }
    setToken(token);
    fetchCurrentUser(token).then((currentUser) => {
      if (!currentUser) {
        clearToken();
        router.replace("/login");
        return;
      }
      setUser(currentUser);
      setLoading(false);
    });

    Promise.all([fetchActiveProducts(), fetchOwnedProductIds(token)]).then(([allProducts, ownedIds]) => {
      const ownedIdSet = new Set(ownedIds);
      setOwnedProducts(allProducts.filter((p) => ownedIdSet.has(p.id)));
    });
  }, [router]);

  async function handleDeleteAccount() {
    const token = getToken();
    if (!token) return;
    const confirmed = window.confirm(
      "Delete your account? This cannot be undone.",
    );
    if (!confirmed) return;

    setDeleting(true);
    const success = await deleteAccount(token);
    if (success) {
      clearToken();
      router.push("/");
      router.refresh();
    } else {
      setDeleting(false);
    }
  }

  if (loading || !user) {
    return <div className="mx-auto max-w-2xl px-4 py-20" />;
  }

  return (
    <div className="mx-auto max-w-2xl px-4 py-12">
      <h1 className="text-2xl font-bold tracking-tight">Your account</h1>
      <Card className="mt-6 flex flex-col gap-2">
        <p className="text-sm text-text-secondary">Signed in as</p>
        <p className="font-medium">{user.displayName ?? user.email}</p>
        <p className="text-sm text-text-muted">{user.email}</p>
      </Card>

      {ownedProducts.length > 0 && token && (
        <Card className="mt-6 flex flex-col gap-3">
          <h2 className="font-semibold">My digital products</h2>
          <div>
            {ownedProducts.map((product) => (
              <OwnedProductRow key={product.id} product={product} token={token} />
            ))}
          </div>
        </Card>
      )}

      <Card className="mt-6 flex flex-col gap-3">
        <h2 className="font-semibold">Saved results and history</h2>
        <p className="text-sm text-text-secondary">
          Coming soon — for now, every tool works fully without an account. Use{" "}
          <a href="/tools" className="text-brand-accent hover:underline">
            any tool
          </a>{" "}
          and grab a share link to save a result.
        </p>
      </Card>

      <Card className="mt-6 flex flex-col gap-3 border-brand-primary/40">
        <h2 className="font-semibold text-brand-primary">Delete account</h2>
        <p className="text-sm text-text-secondary">
          Permanently deletes your account. This cannot be undone.
        </p>
        <Button
          type="button"
          variant="secondary"
          onClick={handleDeleteAccount}
          disabled={deleting}
          className="w-fit border-brand-primary text-brand-primary"
        >
          {deleting ? "Deleting..." : "Delete my account"}
        </Button>
      </Card>
    </div>
  );
}
