import type { Metadata } from "next";
import Link from "next/link";
import "./globals.css";

export const metadata: Metadata = {
  title: { default: "Sthira", template: "%s · Sthira" },
  description:
    "Camera-based CDC STEADI balance and strength tests for home physiotherapy. Pose tracking runs in the browser; video never leaves the device.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className="h-full antialiased">
      <body className="flex min-h-full flex-col">
        {children}
        <footer className="mt-auto border-t border-border px-4 py-4">
          <nav aria-label="Legal" className="mx-auto flex w-full max-w-5xl flex-wrap gap-x-6 gap-y-2 text-sm text-muted">
            <Link href="/privacy" className="inline-flex min-h-11 items-center underline-offset-4 hover:underline">
              Privacy
            </Link>
            <Link href="/terms" className="inline-flex min-h-11 items-center underline-offset-4 hover:underline">
              Terms
            </Link>
            <Link href="/licenses" className="inline-flex min-h-11 items-center underline-offset-4 hover:underline">
              Licences
            </Link>
            <span className="inline-flex min-h-11 items-center">Prototype. Not a medical device.</span>
          </nav>
        </footer>
      </body>
    </html>
  );
}
