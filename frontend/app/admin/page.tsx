"use client";

import { useEffect, useState } from "react";
import AdminAffiliateProductsTable from "@/components/admin/AdminAffiliateProductsTable";
import AdminAiUsageCard from "@/components/admin/AdminAiUsageCard";
import AdminAiSignalsCard from "@/components/admin/AdminAiSignalsCard";
import AdminFeatureFlags from "@/components/admin/AdminFeatureFlags";
import AdminProductsTable from "@/components/admin/AdminProductsTable";
import AdminToolsTable from "@/components/admin/AdminToolsTable";
import AdminUsersPanel from "@/components/admin/AdminUsersPanel";
import AdminPremiumUsersPanel from "@/components/admin/AdminPremiumUsersPanel";
import { Card } from "@/components/ui/Card";
import {
  fetchAdminAffiliateProducts,
  fetchAdminFeatureFlags,
  fetchAdmins,
  fetchAdminProducts,
  fetchAdminTools,
  fetchAiUsageToday,
  fetchAiSignalSummary,
  fetchPremiumUsers,
  ApiError,
  type AdminAffiliateProduct,
  type AdminFeatureFlag,
  type AdminProduct,
  type AdminTool,
  type AdminUserSummary,
  type AdminPremiumUser,
  type AiUsageSummary,
  type AiSignalSummary,
} from "@/lib/api";
import { getToken } from "@/lib/auth";

type LoadState = "loading" | "unauthenticated" | "forbidden" | "error" | "ready";

export default function AdminPage() {
  const [state, setState] = useState<LoadState>("loading");
  const [tools, setTools] = useState<AdminTool[]>([]);
  const [flags, setFlags] = useState<AdminFeatureFlag[]>([]);
  const [admins, setAdmins] = useState<AdminUserSummary[]>([]);
  const [premiumUsers, setPremiumUsers] = useState<AdminPremiumUser[]>([]);
  const [affiliateProducts, setAffiliateProducts] = useState<AdminAffiliateProduct[]>([]);
  const [products, setProducts] = useState<AdminProduct[]>([]);
  const [aiUsage, setAiUsage] = useState<AiUsageSummary | null>(null);
  const [aiSignals, setAiSignals] = useState<AiSignalSummary | null>(null);
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
      fetchPremiumUsers(t),
      fetchAdminAffiliateProducts(t),
      fetchAdminProducts(t),
      fetchAiUsageToday(t),
      fetchAiSignalSummary(t),
    ])
      .then(([toolsResult, flagsResult, adminsResult, premiumUsersResult, affiliateProductsResult, productsResult, aiUsageResult, aiSignalsResult]) => {
        setTools(toolsResult);
        setFlags(flagsResult);
        setAdmins(adminsResult);
        setPremiumUsers(premiumUsersResult);
        setAffiliateProducts(affiliateProductsResult);
        setProducts(productsResult);
        setAiUsage(aiUsageResult);
        setAiSignals(aiSignalsResult);
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

  if (state === "error" || !token || !aiUsage) {
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

      <Card className="mt-6">
        <h2 className="mb-4 font-semibold">Premium access</h2>
        <AdminPremiumUsersPanel initialPremiumUsers={premiumUsers} token={token} />
      </Card>

      <Card className="mt-6">
        <h2 className="mb-4 font-semibold">AI usage today</h2>
        <AdminAiUsageCard initialUsage={aiUsage} token={token} />
      </Card>

      <Card className="mt-6">
        <h2 className="mb-4 font-semibold">AI generation quality signals</h2>
        <AdminAiSignalsCard initialSummary={aiSignals!} token={token} />
      </Card>
    </div>
  );
}
