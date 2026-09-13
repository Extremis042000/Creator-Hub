import Link from "next/link";
import BackendStatus from "./BackendStatus";

export default function SiteFooter() {
  return (
    <footer className="border-t border-border-subtle">
      <div className="mx-auto flex max-w-6xl flex-col gap-4 px-4 py-8 text-sm text-text-secondary sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-wrap gap-4">
          <Link href="/about" className="hover:text-text-primary">
            About
          </Link>
          <Link href="/privacy" className="hover:text-text-primary">
            Privacy
          </Link>
          <Link href="/terms" className="hover:text-text-primary">
            Terms
          </Link>
          <Link href="/contact" className="hover:text-text-primary">
            Contact
          </Link>
        </div>
        <BackendStatus />
      </div>
    </footer>
  );
}
