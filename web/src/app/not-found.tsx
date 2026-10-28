import Link from "next/link";

export default function NotFound() {
  return (
    <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col justify-center gap-4 px-4 py-12">
      <p className="text-sm font-semibold uppercase tracking-wide text-muted">Page not found</p>
      <h1 className="text-3xl font-bold">We could not find that page</h1>
      <p className="text-muted">The link may be old or mistyped. The tests and tools are on the home page.</p>
      <Link
        href="/"
        className="inline-flex min-h-11 w-fit items-center rounded-lg bg-primary px-5 font-semibold text-on-primary hover:bg-primary-hover"
      >
        Go to the home page
      </Link>
    </main>
  );
}
