# SplitSmart design directions

Brief: playful, social, fast. Warm, bright, friendly; bold rounded shapes, one hero colour plus two supporting, a playful display font, emoji used tastefully, bouncy-but-snappy springs, celebratory moments. Must not look like PayFlow.

Open [`design-preview.html`](design-preview.html) in a browser to see both directions, press the buttons and roll the amount. It works offline.

**The ₹ glyph decided the fonts.** I rendered ₹ in 31 OFL fonts. Instrument Serif, DM Serif Display, Bodoni Moda, Young Serif, Sora, Hanken Grotesk, Instrument Sans, Outfit, Urbanist, Onest, JetBrains Mono and Fredoka all lack it, so Android would silently substitute a system ₹ next to the custom digits. Every font proposed below has it. Fredoka, the obvious rounded choice, is out for this reason; Baloo 2 replaces it.

## A. Marigold Mela ⭐ recommended
**Mood:** A festival with friends. Marigold-yellow hero colour on warm cream, deep aubergine ink, mint for money coming in and chilli for money going out. Chunky rounded shapes that squash under your thumb.

**Why I recommend it:** it's warm and unmistakably Indian without borrowing anyone's identity, aubergine ink gives it more character than yet another black-on-yellow, and it couldn't be mistaken for PayFlow. Bricolage Grotesque has a quirk that plain rounded fonts lack.

**Palette** (hero + 2 supporting: marigold + mint + chilli, on aubergine ink)
| Token | Light | Dark |
|---|---|---|
| bg | `#FFF8E8` | `#1C0F26` |
| surface | `#FFFFFF` | `#2A1938` |
| raised | `#FFEFC7` | `#382347` |
| text | `#2B1238` | `#FFF6E3` |
| textSecondary | `#665069` | `#CDBDD0` |
| primary / onPrimary | `#FFB627` / `#2B1238` | `#FFC043` / `#2B1238` |
| accent | `#6B2A86` | `#FFC043` |
| owed (success) | `#0B7A5F` | `#3FE0B8` |
| refining (pending) | `#7E5900` | `#FFD479` |
| you owe (failed) | `#C22F27` | `#FF9A8F` |
| AI (info) | `#6B2A86` | `#E0B3F2` |

**Type:** Bricolage Grotesque (display and numerals, has ₹) + Figtree (UI, has ₹). **Scale:** hero 40/44, display 28/34, title 20/26, body 15/22, label 12/16.

**Shape and texture:** radii 16 / 24 / 32. Chunky solid offset shadows that compress on press. Emoji as tasteful accents on group tiles. Code-drawn marigold-and-confetti illustrations.

**Motion:** bouncy but quick. Snappy 1400/0.85, Bouncy 600/0.55, Gentle 260/0.8. Press 90ms, small 220ms, screen 340ms, stagger 35ms.

**5 signature moments**
1. AI skeleton lines resolve one by one into real bill items.
2. Drag an item onto a member: the avatar gulps, its counter bumps.
3. SETTLED stamp slams down with emoji confetti.
4. "Simplify debts": graph arrows collapse into the fewest payments.
5. FAB bursts open into Scan / Add expense / New group.

### Hero wireframes
```
GROUPS HOME                 AI READS THE BILL           ASSIGN + SPLIT
+------------------------+  +------------------------+  +------------------------+
| SplitSmart         (D) |  | <  Reading your bill   |  | <  Blue Tokai   ₹1,412 |
| +--------------------+ |  |  ✦ AI is on it...      |  |   (D)2  (A)3  (R)1     |
| | You're owed        | |  |                        |  |   avatars gulp + bump  |
| | ₹1,118.70  green   | |  | Cappuccino  x2  ₹360 ✓ |  |                        |
| | You owe ₹600       | |  | Cold Brew   x1  ₹220 ✓ |  | [Equal][Item][Share][%]|
| +--------------------+ |  | Avocado To▌            |  |      .-----.           |
|                        |  | ░░░░░░░░░░░░░░░░░░░░░░ |  |     / donut \  morphs  |
| +--------------------+ |  | ░░░░░░░░░░░░░░░░░░░░░░ |  |     \       /  by mode |
| | ✈ Goa   (D)(A)(R)  | |  | ░░░░░░░░░░░░░░░░░░░░░░ |  |      '-----'           |
| | owed ₹1,118.70     | |  |                        |  | Cappuccino ₹360  ==>   |
| +--------------------+ |  | Tax ₹62.50  Tip ₹100   |  |  drag onto an avatar   |
| | 🏠 Flat 302 (D)(P) | |  | Total ₹1,412.50  ✓     |  | Darshan        ₹293.80 |
| | you owe ₹600       | |  |                        |  | Asha           ₹678.00 |
| +--------------------+ |  |  lines resolve one     |  | Ravi           ₹440.70 |
|                  (+)   |  |  by one, then editable |  | [ Simplify debts ->  ] |
| [Groups][Scan][Me] blob|  | [      Looks good     ]|  | [     Save split     ] |
+------------------------+  +------------------------+  +------------------------+
```

### Contrast (WCAG AA, 4.5:1)
| Mode | Pair | Ratio | AA |
|---|---|---:|---|
| light | `text` on `bg` | 15.92:1 | ✓ |
| light | `text` on `surface` | 16.85:1 | ✓ |
| light | `text` on `raised` | 14.78:1 | ✓ |
| light | `textSecondary` on `bg` | 6.80:1 | ✓ |
| light | `textSecondary` on `surface` | 7.20:1 | ✓ |
| light | `textSecondary` on `raised` | 6.32:1 | ✓ |
| light | `onPrimary` on `primary` | 9.61:1 | ✓ |
| light | `accent` on `bg` | 8.63:1 | ✓ |
| light | `accent` on `surface` | 9.13:1 | ✓ |
| light | `success` on `surface` | 5.30:1 | ✓ |
| light | `pending` on `surface` | 6.33:1 | ✓ |
| light | `failed` on `surface` | 5.63:1 | ✓ |
| light | `info` on `surface` | 9.13:1 | ✓ |
| light | `success` on `bg` | 5.01:1 | ✓ |
| light | `pending` on `bg` | 5.98:1 | ✓ |
| light | `failed` on `bg` | 5.32:1 | ✓ |
| light | `info` on `bg` | 8.63:1 | ✓ |
| dark | `text` on `bg` | 17.06:1 | ✓ |
| dark | `text` on `surface` | 15.10:1 | ✓ |
| dark | `text` on `raised` | 13.03:1 | ✓ |
| dark | `textSecondary` on `bg` | 10.29:1 | ✓ |
| dark | `textSecondary` on `surface` | 9.10:1 | ✓ |
| dark | `textSecondary` on `raised` | 7.86:1 | ✓ |
| dark | `onPrimary` on `primary` | 10.33:1 | ✓ |
| dark | `accent` on `bg` | 11.24:1 | ✓ |
| dark | `accent` on `surface` | 9.94:1 | ✓ |
| dark | `success` on `surface` | 9.71:1 | ✓ |
| dark | `pending` on `surface` | 11.54:1 | ✓ |
| dark | `failed` on `surface` | 7.93:1 | ✓ |
| dark | `info` on `surface` | 9.19:1 | ✓ |
| dark | `success` on `bg` | 10.97:1 | ✓ |
| dark | `pending` on `bg` | 13.04:1 | ✓ |
| dark | `failed` on `bg` | 8.96:1 | ✓ |
| dark | `info` on `bg` | 10.39:1 | ✓ |

## B. Gulabi Pop
**Mood:** Loud, sweet and bubbly. Hot gulabi pink on blush, turquoise and sun-yellow highlights, soft pill shapes everywhere, and a rounded display face from an Indian type foundry.

**Palette**
| Token | Light | Dark |
|---|---|---|
| bg | `#FFF5F9` | `#140F26` |
| surface | `#FFFFFF` | `#211A3A` |
| raised | `#FFE3EE` | `#2E2650` |
| text | `#1D1537` | `#FFF1F7` |
| textSecondary | `#5F5775` | `#C2B9D6` |
| primary / onPrimary | `#D1145B` / `#FFFFFF` | `#FF5C98` / `#240414` |
| accent | `#C01253` | `#FF7DAE` |
| owed (success) | `#00766A` | `#3AD6C2` |
| refining (pending) | `#7E5900` | `#FFD479` |
| you owe (failed) | `#B3261E` | `#FF9C8C` |
| AI (info) | `#007783` | `#5FD8E4` |

**Type:** Baloo 2 (display and numerals, by Ek Type, has ₹) + Nunito (UI, tabular digits, has ₹).

**Shape and texture:** radii 20 / 28 / full pills. Soft coloured glows instead of hard shadows. Bubble-shaped avatars and blobs; illustrations built from circles.

**Motion:** jelly. Snappy 1200/0.8, Bouncy 480/0.5, Gentle 220/0.7. Press 100ms, small 260ms, screen 380ms, stagger 45ms.

**5 signature moments**
1. Selection blob wobbles between tabs in the bottom bar.
2. Donut morphs like jelly between split modes.
3. Bubbles pop as AI items appear on the bill.
4. Settled: confetti bubbles float up and pop.
5. Avatar stack fans out like a hand of cards.

### Hero wireframes
```
GROUPS HOME                 AI READS THE BILL           ASSIGN + SPLIT
+------------------------+  +------------------------+  +------------------------+
| SplitSmart  ( ) ( )    |  | <  Reading your bill   |  | <  Blue Tokai   ₹1,412 |
| (  You're owed       ) |  | ( ✦ AI is on it...   ) |  |  (D)2   (A)3   (R)1    |
| (  ₹1,118.70  glow   ) |  |                        |  |   bubbles squish       |
|                        |  | ( Cappuccino ₹360  o ) |  |                        |
| (✈ Goa     (D)(A)(R) ) |  | ( Cold Brew  ₹220  o ) |  | (Equal)(Item)(Share)(%)|
| (  owed ₹1,118.70    ) |  | ( pop! Avocado...    ) |  |      .-~~~-.           |
|                        |  | ( ░░░░░░░░░░░░░░░░░░ ) |  |     (  jelly )         |
| (🏠 Flat 302 (D)(P)  ) |  | ( ░░░░░░░░░░░░░░░░░░ ) |  |      '-~~~-'           |
| (  you owe ₹600      ) |  |                        |  | Cappuccino ₹360  ==>   |
|                        |  | ( Total ₹1,412.50  o ) |  | Asha           ₹678.00 |
|                 ( + )  |  | (    Looks good      ) |  | Ravi           ₹440.70 |
| ( blob Groups ) Scan Me|  |                        |  | (    Save split      ) |
+------------------------+  +------------------------+  +------------------------+
```

### Contrast (WCAG AA, 4.5:1)
| Mode | Pair | Ratio | AA |
|---|---|---:|---|
| light | `text` on `bg` | 16.20:1 | ✓ |
| light | `text` on `surface` | 17.29:1 | ✓ |
| light | `text` on `raised` | 14.39:1 | ✓ |
| light | `textSecondary` on `bg` | 6.33:1 | ✓ |
| light | `textSecondary` on `surface` | 6.76:1 | ✓ |
| light | `textSecondary` on `raised` | 5.62:1 | ✓ |
| light | `onPrimary` on `primary` | 5.30:1 | ✓ |
| light | `accent` on `bg` | 5.70:1 | ✓ |
| light | `accent` on `surface` | 6.08:1 | ✓ |
| light | `success` on `surface` | 5.53:1 | ✓ |
| light | `pending` on `surface` | 6.33:1 | ✓ |
| light | `failed` on `surface` | 6.54:1 | ✓ |
| light | `info` on `surface` | 5.29:1 | ✓ |
| light | `success` on `bg` | 5.18:1 | ✓ |
| light | `pending` on `bg` | 5.93:1 | ✓ |
| light | `failed` on `bg` | 6.13:1 | ✓ |
| light | `info` on `bg` | 4.96:1 | ✓ |
| dark | `text` on `bg` | 17.03:1 | ✓ |
| dark | `text` on `surface` | 15.06:1 | ✓ |
| dark | `text` on `raised` | 12.71:1 | ✓ |
| dark | `textSecondary` on `bg` | 9.95:1 | ✓ |
| dark | `textSecondary` on `surface` | 8.80:1 | ✓ |
| dark | `textSecondary` on `raised` | 7.42:1 | ✓ |
| dark | `onPrimary` on `primary` | 6.56:1 | ✓ |
| dark | `accent` on `bg` | 7.81:1 | ✓ |
| dark | `accent` on `surface` | 6.91:1 | ✓ |
| dark | `success` on `surface` | 9.09:1 | ✓ |
| dark | `pending` on `surface` | 11.74:1 | ✓ |
| dark | `failed` on `surface` | 8.16:1 | ✓ |
| dark | `info` on `surface` | 9.76:1 | ✓ |
| dark | `success` on `bg` | 10.28:1 | ✓ |
| dark | `pending` on `bg` | 13.27:1 | ✓ |
| dark | `failed` on `bg` | 9.22:1 | ✓ |
| dark | `info` on `bg` | 11.04:1 | ✓ |
