# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Delegated by the user: native Kotlin, Jetpack Compose, Gradle, Android 8.0+.

## Users

Android users who need Telegram to reach its datacenters through a local, app-scoped proxy without VPNService, root access, or device-wide traffic interception.

## Product Purpose

MordaTG runs a loopback-only SOCKS5 endpoint on one persisted random port from the private range `49152..65535`, selecting another spaced port only when the saved one is occupied. It accepts connections explicitly configured by the user in Telegram and relays supported Telegram MTProto traffic through the WebSocket/TLS route proven by KROT's pinned `Flowseal/tg-ws-proxy v1.10.4` runtime.

Success means Telegram can use the local proxy reliably while other device traffic remains untouched, the service is quiet when idle, and the app produces an installable APK with trustworthy local diagnostics and safe self-update support.

## Positioning

Unlike a VPN or system-wide proxy, MordaTG exposes only a loopback SOCKS5 listener and restricts upstream routing to recognized Telegram datacenters. It reuses the compatible KROT MTProto-over-WebSocket transport rather than inventing a new server protocol.

## Operating Context

The user grants the required notification permission on Android 13+ and battery-optimization exemption, starts a foreground service, and adds the currently displayed loopback address as a SOCKS5 proxy in Telegram via a deep link or manual settings. The app does not start the proxy until these required grants are present.

## Capabilities and Constraints

- Android 8.0 (API 26) and newer.
- Kotlin and Jetpack Compose; one installable APK.
- Loopback-only SOCKS5, NO AUTH, TCP CONNECT, IPv4, IPv6, and domain requests.
- No VPNService, root, global proxy changes, traffic capture, telemetry, or third-party advertising SDKs.
- WebSocket over verified TLS with hostname verification; no trust-all mode.
- Event-driven foreground service, bounded connections, cancellation, timeouts, health checks, and local-only diagnostics.
- User-initiated HTTPS update download, SHA-256 verification, and standard Android package installer.
- Safe text/image/link configuration for the first-party morda.online block; no remote HTML or executable content.
- Main UI uses the user-approved composition C: one large status field above one compact Material 3 operating surface.

## Brand Commitments

The current working product name is **MordaTG**, superseding the older `KROT TG` wording in the imported technical brief. KROT remains the compatibility reference. The selected UI is native Material 3 with automatic light/dark mode, Dynamic Color where available, and a restrained teal fallback. The main screen follows composition C. The morda.online row reads “Нужен не только Telegram?” and opens `@morda_online_bot` through a Telegram deep link with an HTTPS fallback. The final launcher icon remains deliberately temporary; the future icon should combine a recognizable face/morda with a Telegram cue without copying Telegram's official mark as the whole icon.

## Evidence on Hand

- Windows KROT project used during compatibility research.
- Pinned upstream: `Flowseal/tg-ws-proxy v1.10.4`, commit `70b982da2ca75637b61f281170e4ed57df763db8`.
- Existing KROT behavior: local MTProto proxy at `127.0.0.1:1443`, `dd` secret link, Telegram WSS endpoints at `/apiws`, and verified TLS.
- Existing KROT mole artwork is available as reference; a custom face/morda + Telegram-cue launcher icon is planned for a later user-approved pass.
- No production update endpoint, morda.online endpoint, release keystore, or final brand assets have been supplied; the app must ship with safe disabled/fallback defaults for those items.

## Product Principles

1. Loopback-only and least privilege by construction.
2. Protocol compatibility is demonstrated with tests, not assumed.
3. Idle means idle: no polling loops, permanent wake locks, or gratuitous network traffic.
4. User-visible control over background service, proxy configuration, links, downloads, and installation.
5. Diagnostics are useful without recording secrets or traffic content.
6. Default UI copy stays concise; protocol detail belongs in diagnostics and documentation, not repeated helper text.

## Accessibility & Inclusion

Use native Android semantics, Material 3 interaction patterns, 48 dp touch targets, scalable text, readable status without color alone, system Back, edge-to-edge insets, and light/dark contrast support.
