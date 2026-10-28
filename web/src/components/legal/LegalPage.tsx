import type { ReactNode } from "react";

export type LegalSection = { heading: string; body: readonly ReactNode[] };

type Props = { title: string; updated: string; intro: string; sections: readonly LegalSection[] };

export function LegalPage({ title, updated, intro, sections }: Props) {
  return (
    <main className="mx-auto flex w-full max-w-2xl flex-col gap-6 px-4 py-10">
      <header className="flex flex-col gap-2">
        <h1 className="text-3xl font-bold">{title}</h1>
        <p className="text-sm text-muted">Last updated {updated}</p>
        <p className="text-lg">{intro}</p>
      </header>
      {sections.map((section) => (
        <section key={section.heading} className="flex flex-col gap-2">
          <h2 className="text-xl font-semibold">{section.heading}</h2>
          {section.body.map((paragraph, index) => (
            <p key={index} className="leading-relaxed text-muted">
              {paragraph}
            </p>
          ))}
        </section>
      ))}
    </main>
  );
}
