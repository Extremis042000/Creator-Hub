"use client";

import { useEffect, useState } from "react";
import AdminAffiliateProductsTable from "@/components/admin/AdminAffiliateProductsTable";
import AdminFeatureFlags from "@/components/admin/AdminFeatureFlags";
import AdminProductsTable from "@/components/admin/AdminProductsTable";
import AdminToolsTable from "@/components/admin/AdminToolsTable";
import AdminUsersPanel from "@/components/admin/AdminUsersPanel";
import { Card } from "@/components/ui/Card";
import {
  fetchAdminAffiliateProducts,
  fetchAdminFeatureFlags,
  fetchAdmins,
  fetchAdminProducts,
  fetchAdminTools,
  ApiError,
  type AdminAffiliateProduct,
  type AdminFeatureFlag,
  type AdminProduct,
  type AdminTool,
  type AdminUserSummary,
} from "@/lib/api";
import { getToken } from "@/lib/auth";

type LoadState = "loading" | "unauthenticated" | "forbidden" | "error" | "ready";

export default function AdminPage() {
  const [state, setState] = useState<LoadState>("loading");
  const [tools, setTools] = useState<AdminTool[]>([]);
  const [flags, setFlags] = useState<AdminFeatureFlag[]>([]);
  const [admins, setAdmins] = useState<AdminUserSummary[]>([]);
  const [affiliateProducts, setAffiliateProducts] = useState<AdminAffiliateProduct[]>([]);
  const [products, setProducts] = useState<AdminProduct[]>([]);
  const [token, setToken] = useState<string | null>(null);

  useEffect(() => {
    const t = getToken();
    if (!t) {
      setState("unauthenticated");
      return;
    }
    setToken(t);

    Promise.all([
      fetchAdminTools(t),
      fetchAdminFeatureFlags(t),
      fetchAdmins(t),
      fetchAdminAffiliateProducts(t),
      fetchAdminProducts(t),
    ])
      .then(([toolsResult, flagsResult, adminsResult, affiliateProductsResult, productsResult]) => {
        setTools(toolsResult);
        setFlags(flagsResult);
        setAdmins(adminsResult);
        setAffiliateProducts(affiliateProductsResult);
        setProducts(productsResult);
        setState("ready");
      })
      .catch((err) => {
        if (err instanceof ApiError && err.body.status === 403) {
          setState("forbidden");
        } else if (err instanceof ApiError && err.body.status === 401) {
          setState("unauthenticated");
        } else {
          setState("error");
        }
      });
  }, []);

  if (state === "loading") {
    return <div className="mx-auto max-w-5xl px-4 py-20" />;
  }

  if (state === "unauthenticated") {
    return (
      <div className="mx-auto max-w-md px-4 py-20 text-center">
        <h1 className="text-xl font-bold">Sign in required</h1>
        <p className="mt-2 text-sm text-text-secondary">
          <a href="/login" className="text-brand-accent hover:underline">
            Sign in
          </a>{" "}
          with an admin account to continue.
        </p>
      </div>
    );
  }

  if (state === "forbidden") {
    return (
      <div className="mx-auto max-w-md px-4 py-20 text-center">
        <h1 className="text-xl font-bold text-brand-primary">Not authorized</h1>
        <p className="mt-2 text-sm text-text-secondary">
          Your account doesn&apos;t have admin access.
        </p>
      </div>
    );
  }

  if (state === "error" || !token) {
    return (
      <div className="mx-auto max-w-md px-4 py-20 text-center">
        <h1 className="text-xl font-bold text-brand-primary">Something went wrong</h1>
        <p className="mt-2 text-sm text-text-secondary">Try refreshing the page.</p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-5xl px-4 py-12">
      <h1 className="text-2xl font-bold tracking-tight">Admin</h1>

      <Card className="mt-6">
        <h2 className="mb-4 font-semibold">Tools</h2>
        <AdminToolsTable initialTools={tools} token={token} />
      </Card>

      <Card className="mt-6">
        <h2 className="mb-4 font-semibold">Feature flags</h2>
        <AdminFeatureFlags initialFlags={flags} token={token} />
      </Card>

      <Card className="mt-6">
        <h2 className="mb-4 font-semibold">Digital products</h2>
        <AdminProductsTable initialProducts={products} token={token} />
      </Card>

      <Card className="mt-6">
        <h2 className="mb-4 font-semibold">Affiliate products</h2>
        <AdminAffiliateProductsTable initialProducts={affiliateProducts} token={token} />
      </Card>

      <Card className="mt-6">
        <h2 className="mb-4 font-semibold">Admins</h2>
        <AdminUsersPanel initialAdmins={admins} token={token} />
      </Card>
    </div>
  );
}
