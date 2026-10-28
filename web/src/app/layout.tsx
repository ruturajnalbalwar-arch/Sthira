import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: { default: "Sthira", template: "%s · Sthira" },
  description:
    "Camera-based CDC STEADI balance and strength tests for home physiotherapy. Pose tracking runs in the browser; video never leaves the device.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className="h-full antialiased">
      <body className="flex min-h-full flex-col">{children}</body>
    </html>
  );
}
