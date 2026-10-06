---
name: MordaTG
description: A calm native control surface for a local Telegram proxy.
colors:
  relay-teal: "#006A6A"
  relay-teal-container: "#9CF1F0"
  relay-teal-ink: "#002020"
  quiet-surface: "#F7FAF9"
  operating-surface: "#ECEFED"
  raised-surface: "#E6E9E8"
  signal-error: "#BA1A1A"
typography:
  headline:
    fontFamily: "Roboto, sans-serif"
    fontSize: "28sp"
    fontWeight: 700
    lineHeight: "36sp"
  title:
    fontFamily: "Roboto, sans-serif"
    fontSize: "16sp"
    fontWeight: 600
    lineHeight: "24sp"
  body:
    fontFamily: "Roboto, sans-serif"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: "20sp"
  label:
    fontFamily: "Roboto, sans-serif"
    fontSize: "12sp"
    fontWeight: 500
    lineHeight: "16sp"
rounded:
  operating-surface: "16dp"
  card: "12dp"
  action: "20dp"
spacing:
  compact: "8dp"
  row: "12dp"
  section: "16dp"
  screen: "20dp"
  state-field: "24dp"
components:
  button-primary:
    backgroundColor: "{colors.relay-teal}"
    textColor: "#FFFFFF"
    typography: "{typography.title}"
    rounded: "{rounded.action}"
    padding: "10dp 24dp"
  status-field:
    backgroundColor: "{colors.relay-teal-container}"
    textColor: "{colors.relay-teal-ink}"
    typography: "{typography.headline}"
    padding: "28dp 24dp"
  operating-card:
    backgroundColor: "{colors.operating-surface}"
    textColor: "{colors.relay-teal-ink}"
    rounded: "{rounded.operating-surface}"
    padding: "12dp 20dp"
---

# Design System: MordaTG

## Overview

**Creative North Star: "The Calm Relay"**

MordaTG is a native Android utility that makes one invisible network state feel legible and controlled. The visual world is quiet Material 3: one generous status field carries the current truth, while a single compact operating surface below contains setup, checks, diagnostics, and the optional morda.online message.

The system favors automatic adaptation over decoration. It follows the device light or dark theme and uses Dynamic Color on Android 12+, with a restrained teal fallback on older devices. Brand character comes from the field-and-surface composition, direct Russian copy, and consistent tonal hierarchy rather than custom chrome.

**Key Characteristics:**

- One dominant state field followed by one continuous operating surface.
- Native Android behavior, semantic color roles, and automatic light/dark adaptation.
- Compact operational rows with clear dividers and a single action per row.
- Calm copy that states what is happening and what the user can do next.

## Colors

The fallback palette uses restrained teal as the active signal and soft near-neutral surfaces for everything operational. Android Dynamic Color may replace the exact hues while preserving the same semantic roles.

### Primary

- **Relay Teal**: primary actions and active emphasis.
- **Relay Teal Container**: the normal status field, large enough to communicate state without alarm.
- **Relay Teal Ink**: foreground content placed on the status container.

### Neutral

- **Quiet Surface**: the app background and top app bar.
- **Operating Surface**: the continuous lower control surface.
- **Raised Surface**: the stronger tonal step for nested or emphasized content.

### Named Rules

**The Semantic Signal Rule.** Teal means ready or active, while the Material error role is reserved for a real startup or port failure.

**The Dynamic Color Rule.** On Android 12+, preserve role and contrast when the system supplies the hue; do not force the fallback teal over the user's device palette.

## Typography

**Display Font:** Roboto (with Android sans-serif fallback)
**Body Font:** Roboto (with Android sans-serif fallback)

**Character:** Platform-native and direct. Hierarchy comes from weight and Material type roles, not an ornamental display face.

### Hierarchy

- **Headline** (bold, 28sp, 36sp line height): the live proxy state only.
- **Title** (semibold, 16–22sp depending on Material role): screen names and operational section titles.
- **Body** (regular, 14–16sp): explanations, setup guidance, and update notes.
- **Label** (medium, 12sp): compact diagnostic labels and supporting metadata.

### Named Rules

**The One Headline Rule.** Only the current proxy state receives headline scale; operational content stays at title or body scale.

## Layout

The main screen is a single vertical reading path. The status field spans the width and uses 24dp horizontal and 28dp vertical padding. The operating surface follows immediately with rounded top corners, 20dp horizontal padding, and rows separated by native dividers. Spacing is built from 8dp and 4dp increments, with 12–16dp between related elements and 20–28dp around major regions.

Content scrolls as one surface on compact phones. Safe drawing insets protect system bars, while rows use weighted text columns so actions remain visible when copy wraps. Secondary screens reuse the same top app bar and 20dp screen padding.

## Elevation & Depth

The system is tonal rather than shadow-driven. Depth comes from the contrast between the status container, the quiet app surface, and the lower operating surface. Native Material components may supply their standard pressed or modal elevation, but resting content does not add decorative shadows.

### Named Rules

**The Tonal-First Rule.** Separate regions with surface roles, spacing, and dividers; do not introduce card shadows to manufacture hierarchy.

## Shapes

The continuous operating surface uses a restrained 16dp top radius. Cards use the Material 3 default 12dp family, and actions use the native pill-like Material button silhouette. The full-width status field remains edge-to-edge so the state reads as the screen's field rather than another card.

## Components

### Buttons

- **Shape:** native Material 3 action silhouette (approximately 20dp corners).
- **Primary:** filled semantic-primary action, used for start/stop, opening Telegram, checking updates, and installing a verified APK.
- **Focus / Pressed:** native Material state layers and accessibility semantics.
- **Secondary:** outlined actions for system settings; text actions for low-emphasis checks and links.

### Cards / Containers

- **Status Field:** a full-width semantic container with centered headline, explanation, primary action, and fixed loopback address.
- **Operating Surface:** one continuous tonal surface with 16dp top corners and divider-separated rows.
- **Information Card:** secondary-container fill with 12dp corners for update and background-work explanations.

### Navigation

Use a standard Material 3 top app bar. The main screen exposes only a three-dot menu; secondary screens use an auto-mirrored back arrow and a clear screen title.

### Operational Row

Text owns the flexible width; a single trailing switch, button, or progress indicator provides the action. A row must remain understandable before the trailing control is read.

## Do's and Don'ts

### Do:

- **Do** keep the current proxy truth in the large status field.
- **Do** use semantic Material colors so error, ready, and disabled states remain accessible in light, dark, and Dynamic Color themes.
- **Do** group operations into one continuous surface and use dividers to support scanning.
- **Do** write visible copy as a concrete state or next action.
- **Do** keep helper text short and show it only when it changes the next action.

### Don't:

- **Don't** split the main screen into a dashboard grid of competing cards.
- **Don't** use color decoratively when it could be mistaken for network state.
- **Don't** add third-party branding, ad-network treatments, ornamental gradients, or attention-seeking motion.
- **Don't** hide mandatory system behavior such as the foreground-service notification behind custom chrome.
- **Don't** repeat protocol, security, or update implementation details on normal screens.
