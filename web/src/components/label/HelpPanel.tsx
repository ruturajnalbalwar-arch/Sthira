import { KEY_INFO, LABEL_KEYS, TRIAL_FAMILY, type Trial } from "@/lib/label/schema";

const CONTROL_KEYS: ReadonlyArray<readonly [string, string]> = [
  ["Z", "Undo the last label"],
  ["Space", "Play / pause (if a button has focus, Space presses that button)"],
  ["← / →", "Step one recorded frame back / forward"],
  ["Shift + ← / →", "Step 1 second back / forward"],
  [", / .", "Slower / faster playback"],
];

function KeyRow({ keys, meaning, muted = false }: { keys: string; meaning: string; muted?: boolean }) {
  return (
    <tr className={`border-b border-border last:border-b-0 ${muted ? "text-muted" : ""}`}>
      <th scope="row" className="py-1 pr-3 text-left align-top">
        <kbd className="rounded border border-border-strong bg-bg px-2 py-0.5 font-mono">{keys}</kbd>
      </th>
      <td className="py-1">{meaning}</td>
    </tr>
  );
}

/** Every shortcut, from labelling-rules.md. Keys for other tests are listed but marked as not used here. */
export function HelpPanel({ trial }: { trial: Trial }) {
  const family = TRIAL_FAMILY[trial];
  return (
    <details open className="rounded-lg border border-border bg-surface p-3">
      <summary className="min-h-11 cursor-pointer content-center font-semibold">Keyboard keys</summary>
      <p className="mt-2 text-sm text-muted">
        Keys do nothing while you are typing in a field. There is no Go key: the recording&apos;s test start is Go,
        and marks before it are refused. For chair stands, press the key at the highest point of each attempt.
      </p>
      <p className="mt-2 text-sm text-muted">
        Marks are taken at the moment you press the key; pause the video before marking for precise timing. A
        mark is placed on the nearest recorded landmark frame; where the video has no landmark data, the key is
        refused.
      </p>
      <p className="mt-2 text-sm text-muted">
        Videos recorded in Chrome (WebM) often have no stored length. They still play, but seeking can be slow
        and may land near a frame rather than on it: after a jump, step with ← / → to settle on a frame.
      </p>
      <table className="mt-2 w-full text-sm">
        <caption className="sr-only">Label keys</caption>
        <tbody>
          {LABEL_KEYS.map((key) => {
            const info = KEY_INFO[key];
            const used = info.family === family;
            return <KeyRow key={key} keys={key} meaning={used ? info.meaning : `${info.meaning} (not used in this test)`} muted={!used} />;
          })}
          {CONTROL_KEYS.map(([keys, meaning]) => (
            <KeyRow key={keys} keys={keys} meaning={meaning} />
          ))}
        </tbody>
      </table>
    </details>
  );
}
