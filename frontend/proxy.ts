import { NextRequest, NextResponse } from "next/server";

/**
 * Custom domain cutover (decisions/08-custom-domain-cutover.md §2):
 * www.creator-hub.co.in is kept resolvable but never canonical -- every
 * request there 308s to the bare domain, preserving path and query.
 * Only the exact "www" host redirects; everything else (localhost,
 * *.workers.dev, the bare domain itself) passes through untouched.
 *
 * Named/exported as `proxy`, not `middleware` -- Next.js 16 renamed the
 * file convention (middleware.ts is deprecated); see
 * node_modules/next/dist/docs/01-app/03-api-reference/03-file-conventions/proxy.md.
 */
const WWW_HOST = "www.creator-hub.co.in";
const CANONICAL_HOST = "creator-hub.co.in";

export function proxy(request: NextRequest) {
  const host = request.headers.get("host");
  if (host === WWW_HOST) {
    const url = new URL(request.url);
    url.host = CANONICAL_HOST;
    url.protocol = "https";
    return NextResponse.redirect(url, 308);
  }
  return NextResponse.next();
}

export const config = {
  matcher: "/:path*",
};
