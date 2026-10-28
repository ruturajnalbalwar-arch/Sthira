import type { Metadata } from "next";
import { LegalPage, type LegalSection } from "@/components/legal/LegalPage";

export const metadata: Metadata = {
  title: "Privacy",
  description: "How Sthira handles camera video, pose data and files: everything stays on your device.",
};

const SECTIONS: readonly LegalSection[] = [
  {
    heading: "Camera video stays on your device",
    body: [
      "Sthira asks for camera permission only when you press Start camera. The video is processed by a pose model that runs inside your browser. Sthira has no server that receives video, pose landmarks, scores or session details.",
      "When you choose to save a recording, the video and landmark files are written to a folder you pick on your own device. Sthira cannot see or retrieve them.",
    ],
  },
  {
    heading: "What we do not collect",
    body: [
      "No accounts, no names, no contact details, no analytics, no advertising trackers and no cookies set by Sthira. Session forms ask for a participant code, never a name.",
    ],
  },
  {
    heading: "Hosting",
    body: [
      "The website files are served by our hosting provider, which may keep standard request logs (such as IP address, browser type and time of the request) to run and secure the service. These logs never contain video or test results, because those are never sent.",
    ],
  },
  {
    heading: "Pilot recordings",
    body: [
      "Recordings made for our pilot study are collected only with signed, written consent from each participant, stored locally by the team, and handled as described in the consent form. Raw videos are deleted after labelling.",
    ],
  },
  {
    heading: "Contact",
    body: ["Questions about privacy: Team The Bakchod Trinity, ruturajnalbalwar07@gmail.com."],
  },
];

export default function PrivacyPage() {
  return (
    <LegalPage
      title="Privacy policy"
      updated="28 October 2026"
      intro="Sthira is a prototype that runs CDC STEADI strength and balance tests with a camera. It is built so that your video and results never leave your device."
      sections={SECTIONS}
    />
  );
}
