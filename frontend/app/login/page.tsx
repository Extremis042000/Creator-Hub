import GoogleSignInButton from "@/components/GoogleSignInButton";
import { buildMetadata } from "@/lib/seo";

export const metadata = buildMetadata({
  title: "Sign in",
  description: "Sign in to EXTREMIS Creator Hub with Google.",
  path: "/login",
});

export default function LoginPage() {
  return (
    <div className="mx-auto flex max-w-md flex-col items-center gap-6 px-4 py-20 text-center">
      <h1 className="text-2xl font-bold tracking-tight">Sign in to EXTREMIS Creator Hub</h1>
      <GoogleSignInButton />
      <p className="text-sm text-text-secondary">
        You don&apos;t need an account to use any tool. Sign in only if you want to save your
        history or unlock premium features in the future.
      </p>
    </div>
  );
}
