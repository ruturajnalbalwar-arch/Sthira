import Link from "next/link";
import normsJson from "@/lib/sthira/norms/norms.json";
import paramsJson from "@/lib/sthira/params/params.json";
import { readProtocolConstants } from "@/lib/sthira/params/protocol";
import { readTimerNorms } from "@/lib/timers/norms";

type TestCard = {
  href: string;
  title: string;
  /** What the person being tested does, in everyday words. */
  task: string;
  /** How long it takes, read from params.json / norms.json; null when that file did not load. */
  time: string | null;
  primary?: boolean;
};

function testCards(): TestCard[] {
  const protocol = readProtocolConstants(paramsJson);
  const timers = readTimerNorms(normsJson);
  const balance = timers.ok ? timers.norms.balance : null;
  const tug = timers.ok ? timers.norms.tug : null;
  return [
    {
      href: "/count",
      title: "Chair stand",
      task: "Stand up and sit down as many times as you can, arms crossed on the chest.",
      time: protocol.ok ? `${protocol.constants.chairStandDurationS} seconds` : null,
      primary: true,
    },
    {
      href: "/balance",
      title: "Balance",
      task: "Stand still in four foot positions, each a little harder.",
      time: balance === null ? null : `${balance.stages.length} × ${balance.stageDurationS} seconds`,
    },
    {
      href: "/tug",
      title: "Timed Up and Go",
      task:
        tug === null
          ? "Stand up, walk a short way at a normal pace, turn, walk back and sit down."
          : `Stand up, walk ${tug.walkDistanceMeters} metres at a normal pace, turn, walk back and sit down.`,
      time: null,
    },
  ];
}

function Card({ card }: { card: TestCard }) {
  const tone = card.primary
    ? "border-primary bg-primary text-on-primary hover:bg-primary-hover"
    : "border-border-strong bg-surface text-text hover:border-primary";
  return (
    <li>
      <Link
        href={card.href}
        className={`flex min-h-24 flex-col gap-1 rounded-2xl border-2 p-5 transition-colors duration-200 ${tone}`}
      >
        <span className="flex flex-wrap items-baseline justify-between gap-x-4">
          <span className="text-2xl font-bold">{card.title}</span>
          {card.time !== null && <span className="text-lg font-semibold">{card.time}</span>}
        </span>
        <span className="text-lg">{card.task}</span>
      </Link>
    </li>
  );
}

export default function Home() {
  return (
    <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col gap-8 px-4 py-8 sm:py-12">
      <header className="flex flex-col gap-3">
        <h1 className="text-3xl font-bold leading-tight text-balance sm:text-4xl">Three short tests of strength and balance</h1>
        <p className="text-lg">
          Your physiotherapist runs these CDC STEADI tests with a phone or laptop camera. Pose tracking happens on this device, and video
          never leaves it. Results are shown next to CDC reference values for the physiotherapist to explain.
        </p>
      </header>
      <nav aria-label="Tests">
        <ul className="flex flex-col gap-4">
          {testCards().map((card) => (
            <Card key={card.href} card={card} />
          ))}
        </ul>
      </nav>
      <section aria-labelledby="safety-heading" className="flex flex-col gap-2 rounded-2xl border-2 border-warn bg-warn-bg p-5 text-text">
        <h2 id="safety-heading" className="text-xl font-bold text-warn">
          Before you start
        </h2>
        <ul className="list-disc space-y-1 pl-6 text-lg">
          <li>Use a sturdy chair without wheels, pushed against a wall.</li>
          <li>Someone stands close by, ready to steady the person.</li>
          <li>Stop at once if there is pain, dizziness or shortness of breath.</li>
        </ul>
      </section>
    </main>
  );
}
