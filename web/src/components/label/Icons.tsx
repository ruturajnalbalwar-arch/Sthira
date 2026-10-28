import type { ReactNode } from "react";

function Icon({ children }: { children: ReactNode }) {
  return (
    <svg
      aria-hidden="true"
      focusable="false"
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      {children}
    </svg>
  );
}

export const PlayIcon = () => (
  <Icon>
    <path d="M7 4l13 8-13 8z" />
  </Icon>
);

export const PauseIcon = () => (
  <Icon>
    <path d="M8 4v16M16 4v16" />
  </Icon>
);

export const FrameBackIcon = () => (
  <Icon>
    <path d="M15 5l-7 7 7 7" />
  </Icon>
);

export const FrameForwardIcon = () => (
  <Icon>
    <path d="M9 5l7 7-7 7" />
  </Icon>
);

export const SecondBackIcon = () => (
  <Icon>
    <path d="M12 5l-7 7 7 7M19 5l-7 7 7 7" />
  </Icon>
);

export const SecondForwardIcon = () => (
  <Icon>
    <path d="M5 5l7 7-7 7M12 5l7 7-7 7" />
  </Icon>
);

export const TrashIcon = () => (
  <Icon>
    <path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3" />
  </Icon>
);

export const SeekIcon = () => (
  <Icon>
    <circle cx="12" cy="12" r="7" />
    <path d="M12 2v3M12 19v3M2 12h3M19 12h3" />
  </Icon>
);

export const UndoIcon = () => (
  <Icon>
    <path d="M9 14L4 9l5-5M4 9h11a5 5 0 010 10h-3" />
  </Icon>
);

export const DownloadIcon = () => (
  <Icon>
    <path d="M12 4v11M7 10l5 5 5-5M5 20h14" />
  </Icon>
);
