import type { Metadata } from "next";
import { LegalPage, type LegalSection } from "@/components/legal/LegalPage";

export const metadata: Metadata = {
  title: "Open-source licences",
  description: "The open-source software and model Sthira is built on, with their licences.",
};

const APACHE = "https://www.apache.org/licenses/LICENSE-2.0";
const MIT = "https://opensource.org/license/mit";

type Component = { name: string; by: string; licence: string; url: string };

const COMPONENTS: readonly Component[] = [
  { name: "MediaPipe Tasks Vision", by: "Google", licence: "Apache License 2.0", url: APACHE },
  { name: "BlazePose GHUM 3D pose model (pose_landmarker_full)", by: "Google", licence: "Apache License 2.0", url: APACHE },
  { name: "Next.js", by: "Vercel", licence: "MIT License", url: MIT },
  { name: "React and React DOM", by: "Meta Platforms", licence: "MIT License", url: MIT },
  { name: "Zod", by: "Colin McDonnell", licence: "MIT License", url: MIT },
  { name: "Tailwind CSS", by: "Tailwind Labs", licence: "MIT License", url: MIT },
];

const SECTIONS: readonly LegalSection[] = [
  {
    heading: "Components",
    body: COMPONENTS.map((c) => (
      <span key={c.name}>
        <strong className="text-text">{c.name}</strong>, by {c.by}.{" "}
        <a href={c.url} target="_blank" rel="noopener noreferrer" className="underline underline-offset-2">
          {c.licence}
        </a>
        .
      </span>
    )),
  },
  {
    heading: "Model use",
    body: [
      "The pose model's card lists fitness and repetition counting as intended uses and says it is not intended for life-critical decisions. Sthira uses it to count chair stands for a physiotherapist to interpret, never to diagnose.",
    ],
  },
  {
    heading: "Sthira",
    body: ["The Android app lists the libraries it bundles (AndroidX, Kotlin, CameraX, protobuf, Guava and others) on its own Open-source licences screen."],
  },
];

export default function LicensesPage() {
  return (
    <LegalPage
      title="Open-source licences"
      updated="28 October 2026"
      intro="Sthira is built on open-source software. We thank the authors and keep their licence terms."
      sections={SECTIONS}
    />
  );
}
