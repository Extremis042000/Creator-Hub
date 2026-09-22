import { getToken } from "./auth";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export type BackendHealth = {
  status: "UP" | "DOWN" | "UNREACHABLE";
};

/**
 * Calls the backend's Actuator health endpoint. Used right now only
 * as a Phase 4 ("project init") connectivity check — see
 * components/BackendStatus.tsx — not as a user-facing feature.
 */
export async function fetchBackendHealth(): Promise<BackendHealth> {
  try {
    const res = await fetch(`${API_BASE_URL}/actuator/health`, {
      cache: "no-store",
    });
    if (!res.ok) return { status: "DOWN" };
    const data = await res.json();
    return { status: data.status === "UP" ? "UP" : "DOWN" };
  } catch {
    return { status: "UNREACHABLE" };
  }
}

export type PublicTool = { slug: string; premiumOnly: boolean };

/**
 * Slug + premiumOnly for every tool, so the static /tools page copy
 * (lib/tools.ts) can be merged with the admin's live "Premium only"
 * setting. Fails soft to an empty list -- a backend hiccup here
 * shouldn't take down the whole tools directory page.
 */
export async function fetchPublicTools(): Promise<PublicTool[]> {
  try {
    const res = await fetch(`${API_BASE_URL}/api/v1/tools`, {
      cache: "no-store",
    });
    if (!res.ok) return [];
    return (await res.json()) as PublicTool[];
  } catch {
    return [];
  }
}

/**
 * Enabled feature flags render as "Coming soon" cards on /tools.
 * Fails soft to an empty list -- a backend hiccup here shouldn't take
 * down the whole tools directory page.
 */
export async function fetchEnabledFeatureFlags(): Promise<string[]> {
  try {
    const res = await fetch(`${API_BASE_URL}/api/v1/feature-flags`, {
      cache: "no-store",
    });
    if (!res.ok) return [];
    return (await res.json()) as string[];
  } catch {
    return [];
  }
}

export type PublicAffiliateProduct = {
  id: string;
  name: string;
  brand: string | null;
  category: string | null;
  priceInfo: string | null;
  merchant: string | null;
  disclosureText: string | null;
  imageUrl: string | null;
};

/** Fails soft to an empty list -- a backend hiccup shouldn't take down the whole /gear page. */
export async function fetchActiveAffiliateProducts(): Promise<PublicAffiliateProduct[]> {
  try {
    const res = await fetch(`${API_BASE_URL}/api/v1/affiliate`, {
      cache: "no-store",
    });
    if (!res.ok) return [];
    return (await res.json()) as PublicAffiliateProduct[];
  } catch {
    return [];
  }
}

/**
 * The public site never links to a product's real affiliateUrl
 * directly -- every click goes through this backend redirect so it's
 * logged server-side (see AffiliateClick), then 302s to the real URL.
 */
export function buildAffiliateRedirectUrl(id: string, sessionRef: string): string {
  const query = sessionRef ? `?sessionRef=${encodeURIComponent(sessionRef)}` : "";
  return `${API_BASE_URL}/api/v1/affiliate/${id}/redirect${query}`;
}

export type GameDeal = {
  title: string;
  salePriceUsd: number;
  normalPriceUsd: number;
  savingsPercent: number;
  thumbnailUrl: string;
  steamRatingPercent: number | null;
  storeName: string;
  dealUrl: string;
};

/** Fails soft to an empty list -- a backend/CheapShark hiccup shouldn't break the page. */
export async function fetchGameDeals(): Promise<GameDeal[]> {
  try {
    const res = await fetch(`${API_BASE_URL}/api/v1/game-deals`, {
      cache: "no-store",
    });
    if (!res.ok) return [];
    return (await res.json()) as GameDeal[];
  } catch {
    return [];
  }
}

export type PublicProduct = {
  id: string;
  categorySlug: string;
  name: string;
  priceCents: number;
  currency: string;
};

/** Fails soft to an empty list -- a backend hiccup shouldn't break the store page. */
export async function fetchActiveProducts(): Promise<PublicProduct[]> {
  try {
    const res = await fetch(`${API_BASE_URL}/api/v1/products`, {
      cache: "no-store",
    });
    if (!res.ok) return [];
    return (await res.json()) as PublicProduct[];
  } catch {
    return [];
  }
}

export type ApiFieldError = { field: string; message: string };

export type ApiErrorBody = {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors?: ApiFieldError[];
};

export class ApiError extends Error {
  body: ApiErrorBody;
  constructor(body: ApiErrorBody) {
    super(body.message);
    this.body = body;
  }
}

/**
 * These tool endpoints work fully anonymously ("no signup, ever") --
 * but if the caller happens to be signed in, attaching their token
 * lets the backend recognize them (e.g. Phase 27's premium-gated AI
 * generation, or Phase 22's premiumOnly tool gate). Previously this
 * never attached a token at all, so a signed-in premium user calling
 * a premiumOnly tool (gaming-description-generator) got a real 401
 * from the live site, and no tool could ever see who was calling --
 * found via live verification while building Phase 27.
 */
async function postJson<TResponse>(path: string, payload: unknown): Promise<TResponse> {
  const token = getToken();
  const res = await fetch(`${API_BASE_URL}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(payload),
  });

  if (!res.ok) {
    const body = (await res.json()) as ApiErrorBody;
    throw new ApiError(body);
  }

  return res.json() as Promise<TResponse>;
}

export type PerformanceCategory =
  | "DEVELOPING"
  | "SOLID"
  | "STRONG"
  | "ELITE"
  | "UNDEFEATED"
  | "NO_DATA";

export type KdCalculatorRequest = {
  kills: number;
  deaths: number;
  save?: boolean;
};

export type KdCalculatorResponse = {
  kdRatio: number | null;
  displayValue: string;
  kills?: number;
  deaths?: number;
  performanceCategory: PerformanceCategory;
  isUndefined: boolean;
  shareToken: string | null;
};

export function calculateKd(request: KdCalculatorRequest): Promise<KdCalculatorResponse> {
  return postJson<KdCalculatorResponse>("/api/v1/tools/kd-calculator", request);
}

export type SupportedGame = "VALORANT" | "CS2" | "CSGO" | "OVERWATCH2" | "APEX_LEGENDS";

export const SUPPORTED_GAME_LABELS: Record<SupportedGame, string> = {
  VALORANT: "Valorant",
  CS2: "CS2",
  CSGO: "CS:GO",
  OVERWATCH2: "Overwatch 2",
  APEX_LEGENDS: "Apex Legends",
};

export type SensitivityConversionRequest = {
  sourceGame: SupportedGame;
  targetGame: SupportedGame;
  dpi: number;
  sourceSensitivity: number;
  save?: boolean;
};

export type SensitivityConversionResponse = {
  targetSensitivity: number;
  effectiveDpi: number;
  sourceCmPer360?: number;
  targetCmPer360?: number;
  formulaVersion?: string;
  conversionNote?: string;
  shareToken: string | null;
};

export function convertSensitivity(
  request: SensitivityConversionRequest,
): Promise<SensitivityConversionResponse> {
  return postJson<SensitivityConversionResponse>(
    "/api/v1/tools/valorant-sensitivity-converter",
    request,
  );
}

export type BgmiConfidence = "HIGH" | "MEDIUM" | "LOW" | "INSUFFICIENT_DATA";
export type ScopeLevel = "NO_SCOPE" | "RED_DOT" | "SCOPE_3X" | "SCOPE_4X_ACOG" | "SNIPER_SCOPE";

export const SCOPE_LEVEL_LABELS: Record<ScopeLevel, string> = {
  NO_SCOPE: "No Scope",
  RED_DOT: "Red Dot",
  SCOPE_3X: "3x Scope",
  SCOPE_4X_ACOG: "4x ACOG",
  SNIPER_SCOPE: "8x / Sniper Scope",
};

export type ScopeRecommendation = {
  scopeLevel: ScopeLevel;
  settingName: string;
  recommendedMin?: number;
  recommendedMax?: number;
  recommendedStartingValue?: number;
  confidence: BgmiConfidence;
};

export type BgmiSensitivityRequest = {
  usesGyroscope: boolean;
  save?: boolean;
};

export type BgmiSensitivityResponse = {
  recommendations: ScopeRecommendation[];
  disclaimer: string;
  shareToken: string | null;
};

export function recommendBgmiSensitivity(
  request: BgmiSensitivityRequest,
): Promise<BgmiSensitivityResponse> {
  return postJson<BgmiSensitivityResponse>("/api/v1/tools/bgmi-sensitivity-helper", request);
}

export type VideoType = "HIGHLIGHT" | "TUTORIAL" | "MONTAGE" | "VLOG" | "LIVESTREAM_RECAP";
export type Tone = "HYPE" | "CASUAL" | "COMPETITIVE" | "FUNNY";

export const VIDEO_TYPE_LABELS: Record<VideoType, string> = {
  HIGHLIGHT: "Highlight",
  TUTORIAL: "Tutorial",
  MONTAGE: "Montage",
  VLOG: "Vlog",
  LIVESTREAM_RECAP: "Livestream Recap",
};

export const TONE_LABELS: Record<Tone, string> = {
  HYPE: "Hype",
  CASUAL: "Casual",
  COMPETITIVE: "Competitive",
  FUNNY: "Funny",
};

export type TitleGeneratorRequest = {
  game: string;
  topic: string;
  videoType: VideoType;
  tone: Tone;
  keywords?: string[];
  save?: boolean;
};

export type TitleGeneratorResponse = {
  titles: string[];
  shortFormTitles: string[];
  shareToken: string | null;
  /** Phase 29: present only when AI generated this result -- pass it to refineTitles() to iterate ("make it punchier"). */
  refineSessionId: string | null;
};

export function generateTitles(request: TitleGeneratorRequest): Promise<TitleGeneratorResponse> {
  return postJson<TitleGeneratorResponse>("/api/v1/tools/gaming-title-generator", request);
}

/** Requires premium (same as the AI path on generate()) -- there's no template fallback for "refine this result". */
export function refineTitles(sessionId: string, message: string): Promise<TitleGeneratorResponse> {
  return postJson<TitleGeneratorResponse>("/api/v1/tools/gaming-title-generator/refine", { sessionId, message });
}

export type SocialLink = { platform: string; url: string };

export type DescriptionGeneratorRequest = {
  game: string;
  topic: string;
  channelName: string;
  keywords?: string[];
  socialLinks?: SocialLink[];
  save?: boolean;
};

export type DescriptionGeneratorResponse = {
  description: string;
  seoKeywordsSection: string;
  hashtags: string[];
  shareToken: string | null;
  /** Phase 29: present only when AI generated this result -- pass it to refineDescription() to iterate ("make it shorter"). */
  refineSessionId: string | null;
};

export function generateDescription(
  request: DescriptionGeneratorRequest,
): Promise<DescriptionGeneratorResponse> {
  return postJson<DescriptionGeneratorResponse>(
    "/api/v1/tools/gaming-description-generator",
    request,
  );
}

/** Requires premium (same as the AI path on generate()) -- there's no template fallback for "refine this result". */
export function refineDescription(sessionId: string, message: string): Promise<DescriptionGeneratorResponse> {
  return postJson<DescriptionGeneratorResponse>("/api/v1/tools/gaming-description-generator/refine", {
    sessionId,
    message,
  });
}

export type CurrentUser = {
  id: string;
  email: string;
  displayName: string | null;
  avatarUrl: string | null;
  isAdmin: boolean;
  hasPremiumAccess: boolean;
};

export type AuthResponse = {
  token: string;
  user: CurrentUser;
};

export function signInWithGoogle(idToken: string): Promise<AuthResponse> {
  return postJson<AuthResponse>("/api/v1/auth/google", { idToken });
}

export async function fetchCurrentUser(token: string): Promise<CurrentUser | null> {
  const res = await fetch(`${API_BASE_URL}/api/v1/auth/me`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (!res.ok) return null;
  return res.json() as Promise<CurrentUser>;
}

export async function deleteAccount(token: string): Promise<boolean> {
  const res = await fetch(`${API_BASE_URL}/api/v1/auth/me`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${token}` },
  });
  return res.ok;
}

async function authedRequest<TResponse>(
  method: string,
  path: string,
  token: string,
  payload?: unknown,
): Promise<TResponse> {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(payload !== undefined ? { "Content-Type": "application/json" } : {}),
    },
    body: payload !== undefined ? JSON.stringify(payload) : undefined,
    cache: "no-store",
  });

  if (!res.ok) {
    const body = (await res.json()) as ApiErrorBody;
    throw new ApiError(body);
  }

  return res.json() as Promise<TResponse>;
}

export type AdminTool = {
  id: string;
  toolType: string;
  slug: string;
  name: string;
  category: string | null;
  premiumOnly: boolean;
};

export type AdminToolUpdate = {
  name: string;
  category: string | null;
  premiumOnly: boolean;
};

export function fetchAdminTools(token: string): Promise<AdminTool[]> {
  return authedRequest<AdminTool[]>("GET", "/api/v1/admin/tools", token);
}

export function updateAdminTool(
  token: string,
  id: string,
  update: AdminToolUpdate,
): Promise<AdminTool> {
  return authedRequest<AdminTool>("PATCH", `/api/v1/admin/tools/${id}`, token, update);
}

export type AdminFeatureFlag = {
  key: string;
  enabled: boolean;
  rolloutPercent: number;
};

export function fetchAdminFeatureFlags(token: string): Promise<AdminFeatureFlag[]> {
  return authedRequest<AdminFeatureFlag[]>("GET", "/api/v1/admin/feature-flags", token);
}

export function upsertAdminFeatureFlag(
  token: string,
  key: string,
  update: { enabled: boolean; rolloutPercent: number },
): Promise<AdminFeatureFlag> {
  return authedRequest<AdminFeatureFlag>(
    "PUT",
    `/api/v1/admin/feature-flags/${encodeURIComponent(key)}`,
    token,
    update,
  );
}

export type AdminUserSummary = {
  userId: string | null; // null if an ADMIN_EMAILS entry has never signed in yet
  email: string;
  displayName: string | null;
  source: "ENV" | "GRANTED";
};

export function fetchAdmins(token: string): Promise<AdminUserSummary[]> {
  return authedRequest<AdminUserSummary[]>("GET", "/api/v1/admin/admins", token);
}

/** 204 No Content on success -- no JSON body to parse. */
async function authedRequestNoContent(
  method: string,
  path: string,
  token: string,
  payload?: unknown,
): Promise<void> {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(payload !== undefined ? { "Content-Type": "application/json" } : {}),
    },
    body: payload !== undefined ? JSON.stringify(payload) : undefined,
  });
  if (!res.ok) {
    const body = (await res.json()) as ApiErrorBody;
    throw new ApiError(body);
  }
}

/** Product ids the signed-in user already owns (entitled to download). */
export function fetchOwnedProductIds(token: string): Promise<string[]> {
  return authedRequest<string[]>("GET", "/api/v1/products/owned", token);
}

export function testPurchaseProduct(token: string, productId: string): Promise<{ orderId: string }> {
  return authedRequest<{ orderId: string }>("POST", `/api/v1/products/${productId}/test-purchase`, token);
}

/** Returns a short-lived signed download URL -- fetch a fresh one each time, don't cache it. */
export function fetchProductDownloadUrl(token: string, productId: string): Promise<{ downloadUrl: string }> {
  return authedRequest<{ downloadUrl: string }>("GET", `/api/v1/products/${productId}/download`, token);
}

export type AdminProduct = {
  id: string;
  categorySlug: string;
  name: string;
  priceCents: number;
  currency: string;
  fileRef: string;
  active: boolean;
};

export type AdminProductInput = {
  categorySlug: string;
  name: string;
  priceCents: number;
  currency: string;
  fileRef: string;
};

export function fetchAdminProducts(token: string): Promise<AdminProduct[]> {
  return authedRequest<AdminProduct[]>("GET", "/api/v1/admin/products", token);
}

export function createAdminProduct(token: string, input: AdminProductInput): Promise<AdminProduct> {
  return authedRequest<AdminProduct>("POST", "/api/v1/admin/products", token, input);
}

export function updateAdminProduct(
  token: string,
  id: string,
  input: AdminProductInput & { active: boolean },
): Promise<AdminProduct> {
  return authedRequest<AdminProduct>("PATCH", `/api/v1/admin/products/${id}`, token, input);
}

export type AdminAffiliateProduct = {
  id: string;
  name: string;
  brand: string | null;
  category: string | null;
  priceInfo: string | null;
  affiliateUrl: string;
  merchant: string | null;
  region: string | null;
  disclosureText: string | null;
  imageUrl: string | null;
  active: boolean;
};

export type AdminAffiliateProductInput = {
  name: string;
  brand: string | null;
  category: string | null;
  priceInfo: string | null;
  affiliateUrl: string;
  merchant: string | null;
  region: string | null;
  disclosureText: string | null;
  imageUrl: string | null;
};

export function fetchAdminAffiliateProducts(token: string): Promise<AdminAffiliateProduct[]> {
  return authedRequest<AdminAffiliateProduct[]>("GET", "/api/v1/admin/affiliate-products", token);
}

export function createAdminAffiliateProduct(
  token: string,
  input: AdminAffiliateProductInput,
): Promise<AdminAffiliateProduct> {
  return authedRequest<AdminAffiliateProduct>("POST", "/api/v1/admin/affiliate-products", token, input);
}

export function updateAdminAffiliateProduct(
  token: string,
  id: string,
  input: AdminAffiliateProductInput & { active: boolean },
): Promise<AdminAffiliateProduct> {
  return authedRequest<AdminAffiliateProduct>("PATCH", `/api/v1/admin/affiliate-products/${id}`, token, input);
}

export function grantAdmin(token: string, email: string): Promise<void> {
  return authedRequestNoContent("POST", "/api/v1/admin/admins", token, { email });
}

export function revokeAdmin(token: string, userId: string): Promise<void> {
  return authedRequestNoContent("DELETE", `/api/v1/admin/admins/${userId}`, token);
}

export type AdminPremiumUser = {
  userId: string;
  email: string;
  displayName: string | null;
  grantedAt: string;
};

export function fetchPremiumUsers(token: string): Promise<AdminPremiumUser[]> {
  return authedRequest<AdminPremiumUser[]>("GET", "/api/v1/admin/premium-grants", token);
}

export function grantPremium(token: string, email: string): Promise<void> {
  return authedRequestNoContent("POST", "/api/v1/admin/premium-grants", token, { email });
}

export function revokePremium(token: string, userId: string): Promise<void> {
  return authedRequestNoContent("DELETE", `/api/v1/admin/premium-grants/${userId}`, token);
}

/** Today (UTC) so far -- see backend AiUsageService. Not a running total across all time. */
export type AiUsageSummary = {
  totalCalls: number;
  successCount: number;
  failureCount: number;
  throttledCount: number;
  totalInputTokens: number;
  totalOutputTokens: number;
  averageLatencyMs: number;
  callsByProvider: Record<string, number>;
  dailyCallCeiling: number;
};

export function fetchAiUsageToday(token: string): Promise<AiUsageSummary> {
  return authedRequest<AiUsageSummary>("GET", "/api/v1/admin/ai/usage-today", token);
}
