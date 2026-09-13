import Link from "next/link";
import AuthNav from "./AuthNav";

export default function SiteHeader() {
  return (
    <header className="sticky top-0 z-10 border-b border-border-subtle bg-bg-canvas/95 backdrop-blur">
      <nav className="mx-auto flex max-w-6xl items-center justify-between px-4 py-4">
        <Link href="/" className="text-lg font-bold tracking-tight">
          EXTREMIS <span className="text-brand-primary">CREATOR HUB</span>
        </Link>
        <div className="flex items-center gap-6 text-sm text-text-secondary">
          <Link href="/tools" className="hover:text-text-primary">
            Tools
          </Link>
          <Link href="/deals" className="hover:text-text-primary">
            Deals
          </Link>
          <Link href="/gear" className="hover:text-text-primary">
            Gear
          </Link>
          <Link href="/store" className="hover:text-text-primary">
            Store
          </Link>
          <Link href="/pricing" className="hover:text-text-primary">
            Pricing
          </Link>
          <AuthNav />
        </div>
      </nav>
    </header>
  );
}
