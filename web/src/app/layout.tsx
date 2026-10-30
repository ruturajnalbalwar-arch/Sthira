import type { Metadata, Viewport } from "next";
import Link from "next/link";
import { TextSizeSwitch } from "@/components/ui/TextSizeSwitch";
import { textSizeBootScript } from "@/lib/ui/textSize";
import "./globals.css";

export const metadata: Metadata = {
  title: { default: "Sthira", template: "%s · Sthira" },
  description:
    "Camera-based CDC STEADI balance and strength tests for home physiotherapy. Pose tracking runs in the browser; video never leaves the device.",
};

/** Pinch zoom stays on: older eyes need it, and blocking it fails WCAG 1.4.4. */
export const viewport: Viewport = { width: "device-width", initialScale: 1 };

const footerLink = "inline-flex min-h-12 items-center underline underline-offset-4";

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    // The boot script sets data-text-size before React hydrates, so the attribute may differ from the server's.
    <html lang="en" className="h-full antialiased" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: textSizeBootScript() }} />
      </head>
      <body className="flex min-h-full flex-col">
        <header className="border-b border-border bg-surface px-4 py-2">
          <div className="mx-auto flex w-full max-w-5xl items-center justify-between gap-3">
            <Link href="/" className="inline-flex min-h-12 items-center text-xl font-black tracking-tight text-primary">
              Sthira
              <span className="sr-only">, home</span>
            </Link>
            <TextSizeSwitch />
          </div>
        </header>
        {children}
        <footer className="mt-auto border-t border-border px-4 py-4">
          <div className="mx-auto flex w-full max-w-5xl flex-col gap-2 text-base text-muted">
            <nav aria-label="Legal" className="flex flex-wrap gap-x-6">
              <Link href="/privacy" className={footerLink}>
                Privacy
              </Link>
              <Link href="/terms" className={footerLink}>
                Terms
              </Link>
              <Link href="/licenses" className={footerLink}>
                Licences
              </Link>
            </nav>
            <p>Prototype. Not a medical device. Sthira does not diagnose.</p>
            {/* Pilot tooling is for the Sthira team, so it stays out of the patient-facing home screen. */}
            <nav aria-label="Team tools" className="flex flex-wrap items-center gap-x-6 text-sm">
              <span>For the Sthira team:</span>
              <Link href="/record" className={footerLink}>
                Record pilot session
              </Link>
              <Link href="/label" className={footerLink}>
                Label recordings
              </Link>
            </nav>
          </div>
        </footer>
      </body>
    </html>
  );
}
