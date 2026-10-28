import type { Metadata } from "next";
import { LegalPage, type LegalSection } from "@/components/legal/LegalPage";

export const metadata: Metadata = {
  title: "Terms",
  description: "Terms of use for the Sthira prototype: a measurement aid for physiotherapists, not a medical device.",
};

const SECTIONS: readonly LegalSection[] = [
  {
    heading: "A prototype, not a medical device",
    body: [
      "Sthira is an early research prototype. It has not been clinically validated and is not a certified medical device. It does not diagnose any condition and does not predict falls.",
      "Scores are shown next to CDC STEADI reference values, which come from US data and have not been validated for older adults in India. A qualified physiotherapist must interpret every result and decide on care.",
    ],
  },
  {
    heading: "Safety during tests",
    body: [
      "Run tests only under the supervision of a physiotherapist or trained helper, with a spotter standing close by. Use a stable chair without wheels, placed against a wall, and clear the floor of anything that could cause a trip. Stop the test at once if the person feels unwell, dizzy or unsteady.",
    ],
  },
  {
    heading: "Your responsibilities",
    body: [
      "Get the person's consent before recording them. Store any saved recordings securely and delete them when they are no longer needed. Use participant codes, not names.",
    ],
  },
  {
    heading: "No warranty",
    body: [
      "Sthira is provided as is, without any warranty. Counts can be wrong, for example in poor light, at a low frame rate or when the person leaves the frame. The team is not liable for decisions made using its output.",
    ],
  },
  {
    heading: "Contact",
    body: ["Team The Bakchod Trinity, ruturajnalbalwar07@gmail.com."],
  },
];

export default function TermsPage() {
  return (
    <LegalPage
      title="Terms of use"
      updated="28 October 2026"
      intro="By using Sthira you agree to these terms. Please read the safety section before running any test."
      sections={SECTIONS}
    />
  );
}
