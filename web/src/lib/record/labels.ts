import { AGE_BANDS, type CLOTHING, type TRIALS, type VIEW_ANGLES } from "./schema";

/* Human-readable option text for the session form. Keys are typed so a new enum value cannot be missed. */

/** Age bands are shown exactly as written in the schema (and the protocol log). */
export const AGE_BAND_LABELS: Readonly<Record<(typeof AGE_BANDS)[number], string>> = Object.fromEntries(
  AGE_BANDS.map((band) => [band, band]),
) as Record<(typeof AGE_BANDS)[number], string>;

export const CLOTHING_LABELS: Readonly<Record<(typeof CLOTHING)[number], string>> = {
  saree: "Saree",
  dhoti: "Dhoti",
  lungi: "Lungi",
  "salwar-kameez": "Salwar kameez",
  "kurta-pyjama": "Kurta pyjama",
  "track-pants": "Track pants",
  other: "Other",
};

export const TRIAL_LABELS: Readonly<Record<(typeof TRIALS)[number], string>> = {
  "chair-stand-30s": "30-second chair stand",
  "half-stands": "3 deliberate half-stands",
  "arm-assisted-stands": "3 arm-assisted stands",
  "balance-side-by-side": "Balance 1: feet side by side",
  "balance-semi-tandem": "Balance 2: semi-tandem",
  "balance-tandem": "Balance 3: tandem",
  "balance-single-leg": "Balance 4: single leg",
  tug: "Timed Up and Go (TUG)",
};

export const VIEW_ANGLE_LABELS: Readonly<Record<(typeof VIEW_ANGLES)[number], string>> = {
  "45-degree": "45 degrees to the front (preferred)",
  side: "Side-on",
  front: "Front-on",
};
