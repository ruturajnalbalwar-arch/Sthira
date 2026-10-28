import Link from "next/link";

const linkClass =
  "inline-flex min-h-11 items-center justify-center rounded-lg px-5 text-base font-semibold transition-colors duration-200";

const secondaryClass = "border border-border-strong bg-surface hover:bg-bg";

export default function Home() {
  return (
    <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col justify-center gap-8 px-4 py-12">
      <header className="flex flex-col gap-3">
        <p className="text-sm font-semibold uppercase tracking-widest text-primary">Sthira</p>
        <h1 className="text-4xl font-bold leading-tight text-balance sm:text-5xl">
          Steady scores for home physiotherapy
        </h1>
        <p className="text-lg text-muted">
          Runs the CDC STEADI chair-stand, balance and Timed Up-and-Go tests with a phone or laptop camera.
          Pose tracking happens in this browser, and video never leaves the device. Results are shown next to
          CDC reference norms for the physiotherapist to interpret; Sthira does not diagnose.
        </p>
      </header>
      <nav aria-label="Tools" className="flex flex-col gap-3">
        <Link href="/count" className={`${linkClass} min-h-14 bg-primary text-lg text-on-primary hover:bg-primary-hover sm:self-start`}>
          Run a chair-stand test
        </Link>
        <div className="flex flex-col gap-3 sm:flex-row">
          <Link href="/record" className={`${linkClass} ${secondaryClass}`}>
            Record a pilot session
          </Link>
          <Link href="/label" className={`${linkClass} ${secondaryClass}`}>
            Label recordings
          </Link>
        </div>
      </nav>
    </main>
  );
}
