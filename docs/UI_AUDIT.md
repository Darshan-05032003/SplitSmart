# SplitSmart UI audit

Audited 2026-11-09 from the code at `578ae11`. "Before" screenshots are in [`screenshots/before/`](screenshots/before/), taken on the Pixel 7 emulator.

## Whole-app findings
| Area | Finding | Evidence |
|---|---|---|
| Motion | Effectively none. One `tween()` in the UI; no springs or animated state changes. | Code scan of `ui/` |
| Transitions | Navigation's default crossfade only. | 0 `enterTransition`/`exitTransition` |
| Touch feedback | Default ripple; no haptics, even on saving or settling. | 0 haptic call sites |
| Type | System Roboto; a playful app with a neutral voice. | No `res/font` |
| Colour | Coral gradient looks fine but generic; 6 raw `Color(0x…)` values outside the theme. | Code scan |
| Spacing | 236 literal `.dp` values in screens. | Code scan |
| Dialogs | Stock Material dialogs for "which group?", QR, add member and add bill; these moments deserve sheets. | Screenshots |
| Testing | No screenshot tests. | Not in `build.gradle.kts` |
| Accessibility | Reduce motion and 1.3× font scale not handled or verified. | Code, tests |
| First run | No onboarding: a new user lands on an empty list. No splash hand-off. | Navigation, build file |

## Screen by screen
| Screen | Generic or amateur | Missing | Priority |
|---|---|---|---|
| **Groups home** | Good bones (balance card, emoji tiles) but static; "you owe / you're owed" is a small chip. | Balance colour-morph with odometer, staggered list, swipe to archive with undo, expanding FAB menu, skeleton loading | High |
| **Empty state** | Friendly, but a static composition. | Layered motion, playful illustration | Medium |
| **Group detail** | Very tall and dense: summary, members, add-bill, debts and bills all stacked; members and bills need scrolling. Delete icon sits next to the title. | Tabs or clearer hierarchy, fan-out avatars, destructive action moved away | High |
| **Scan** | Static brackets; dull pill hint. | Laser sweep, live text-block outlines, capture flash and haptic | High |
| **OCR result** | A raw monospace text wall is a technical artefact, not a user moment. | Replace with the AI-reading transition | High |
| **Review** | Three outlined fields per item make the form heavy; only 2–3 items fit. The refining banner is static and the result swaps in abruptly. | Compact rows with inline edit, line-by-line resolve animation, honest fallback state | High |
| **Split** | Chip rows for every item become a wall; mode switch is plain chips and nothing shows the shares visually. | Drag onto avatars, donut, rolling amounts, long-press multi-select | High |
| **Settle up** | Solid structure, plain buttons; QR is a dialog. | Shine sweep, simplify-debts graph, status card on return from UPI, settled celebration | High |
| **Bill detail** | Clear, static. | Shared-element entry | Medium |
| **Dark theme** | Reasonable after the recent fix; coral on brown-black lacks identity. | Designed dark palette | Medium |

## Not built yet, required by the brief
- Onboarding (3 pages).
- Bottom navigation.
- **Settled state and settlement history:** settlements aren't recorded today, so "settled" needs a Room migration (v4).
- **Archive group:** also needs a data-model change.
- Insights card; history timeline.
- Debug-only design catalog.
