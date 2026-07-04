// Minimal pixel-flavored SVG icons (currentColor, 1.8 stroke) — no emoji.
type P = { className?: string }
const base = 'inline-block'
const svg = (path: React.ReactNode) => (props: P) =>
  (
    <svg
      viewBox="0 0 24 24"
      width="1em"
      height="1em"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={`${base} ${props.className ?? ''}`}
      aria-hidden="true"
    >
      {path}
    </svg>
  )

export const IconDex = svg(
  <>
    <rect x="4" y="3" width="16" height="18" rx="1" />
    <circle cx="9" cy="8" r="2" />
    <path d="M14 7h3M14 10h3M7 14h10M7 17h10" />
  </>,
)
export const IconMap = svg(
  <>
    <path d="M9 4 4 6v14l5-2 6 2 5-2V4l-5 2-6-2Z" />
    <path d="M9 4v14M15 6v14" />
  </>,
)
export const IconScroll = svg(
  <>
    <path d="M6 4h11a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H8" />
    <path d="M6 4a2 2 0 0 0-2 2v1h4M9 9h7M9 13h7M9 17h4" />
  </>,
)
export const IconMove = svg(
  <>
    <path d="M12 3v4M12 17v4M3 12h4M17 12h4" />
    <circle cx="12" cy="12" r="3.5" />
  </>,
)
export const IconBag = svg(
  <>
    <path d="M6 8h12l-1 12H7L6 8Z" />
    <path d="M9 8V6a3 3 0 0 1 6 0v2" />
  </>,
)
export const IconTrainer = svg(
  <>
    <circle cx="12" cy="8" r="3.2" />
    <path d="M5 20a7 7 0 0 1 14 0" />
  </>,
)
export const IconTypes = svg(
  <>
    <circle cx="12" cy="12" r="8.5" />
    <path d="M12 3.5v17M3.5 12h17" />
  </>,
)
export const IconHome = svg(
  <>
    <path d="M4 11 12 4l8 7" />
    <path d="M6 10v9h12v-9" />
  </>,
)
export const IconBack = svg(<path d="M14 6l-6 6 6 6" />)
export const IconSearch = svg(
  <>
    <circle cx="11" cy="11" r="6" />
    <path d="m20 20-4-4" />
  </>,
)
export const IconStar = svg(
  <path d="M12 3.5 14.6 9l6 .5-4.6 4 1.5 5.8L12 16.5 6.5 19.3 8 13.5 3.4 9.5l6-.5L12 3.5Z" />,
)

// --- v3 section icons ---
export const IconBoss = svg(
  <>
    <path d="M5 9 7 5l3 3 2-4 2 4 3-3 2 4v8H5V9Z" />
    <path d="M9 14h.01M15 14h.01" />
  </>,
)
export const IconCard = svg(
  <>
    <rect x="5" y="4" width="14" height="16" rx="1" />
    <circle cx="12" cy="10" r="2.4" />
    <path d="M8 16h8" />
  </>,
)
export const IconFurniture = svg(
  <>
    <path d="M5 11V8a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v3" />
    <path d="M4 11h16v5M6 16v3M18 16v3" />
  </>,
)
export const IconCamp = svg(
  <>
    <path d="M12 4 4 19h16L12 4Z" />
    <path d="M12 10 8.5 19h7L12 10Z" />
  </>,
)
export const IconSquadron = svg(
  <>
    <circle cx="8" cy="9" r="2.4" />
    <circle cx="16" cy="9" r="2.4" />
    <path d="M3 19a5 5 0 0 1 10 0M11 19a5 5 0 0 1 10 0" />
  </>,
)
export const IconQuest = svg(
  <>
    <path d="M7 4h7l4 4v12H7z" />
    <path d="M14 4v4h4M9.5 13l1.8 1.8 3.2-3.4" />
  </>,
)
export const IconTrophy = svg(
  <>
    <path d="M7 4h10v4a5 5 0 0 1-10 0V4Z" />
    <path d="M7 6H4v1a3 3 0 0 0 3 3M17 6h3v1a3 3 0 0 1-3 3M9 20h6M12 13v4" />
  </>,
)
export const IconBook = svg(
  <>
    <path d="M5 4h9a2 2 0 0 1 2 2v14H7a2 2 0 0 1-2-2V4Z" />
    <path d="M16 6h3v14H7M9 9h4M9 12h4" />
  </>,
)
export const IconCog = svg(
  <>
    <circle cx="12" cy="12" r="3" />
    <path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1" />
  </>,
)
export const IconGraph = svg(
  <>
    <circle cx="6" cy="6" r="2.2" />
    <circle cx="18" cy="9" r="2.2" />
    <circle cx="9" cy="18" r="2.2" />
    <path d="M7.7 7.2 16 8.4M7.4 16.3l1-7M10.8 17l5.5-6.4" />
  </>,
)
export const IconSpark = svg(
  <>
    <path d="M12 3v6M12 15v6M3 12h6M15 12h6" />
    <circle cx="12" cy="12" r="2.2" />
  </>,
)
export const IconInfo = svg(
  <>
    <circle cx="12" cy="12" r="8.5" />
    <path d="M12 11v5M12 8h.01" />
  </>,
)
export const IconGlobe = svg(
  <>
    <circle cx="12" cy="12" r="8.5" />
    <path d="M3.5 12h17M12 3.5c2.5 2.5 2.5 14.5 0 17M12 3.5c-2.5 2.5-2.5 14.5 0 17" />
  </>,
)
export const IconMore = svg(<path d="M5 12h.01M12 12h.01M19 12h.01" />)
