# Cyber Phone Inspector report

Device: Samsung SM-S926B, Android 16 (API 36)
Scan time: 2026-10-21T12:00:00Z (UTC), 2026-10-21 08:00 EDT (local)
Indicator snapshot: 2026-10-21
Read only: this scan changed nothing on the phone.

## What this scan touched
- Read the installed app list, permissions, device admin, accessibility and notification settings. No consent needed; nothing changed.
- Usage access: read which apps ran foreground services in the last 7 days. You granted this; revoke it any time in Settings.
- Local VPN: observed DNS lookups for the monitor window. You granted this; it stopped when the scan ended. No traffic was redirected or stored.

## Apps

### Turbo Cleaner (com.cleaner.turbo)
- What it is: com.cleaner.turbo contains 1 tracker kit (Adbrix) and did not come from an app store. Five or more advertising trackers, or a profiling tracker, in an app installed outside a store is the pattern of adware.
- What it can reach: nothing beyond its own data
- How long it has been there: since at latest 2026-09-05
- Where it sends data: no network observed
- Severity: medium
- What to do: If you do not need com.cleaner.turbo, uninstall it. If you do, expect it to sell what you do inside it.

- Low: Turbo Cleaner (com.cleaner.turbo): installed outside a store, no sensitive grants; reaches: nothing beyond its own data; since at latest 2026-09-05; sends data: no network observed.
- Low: Coin Rush (com.free.coin.game): trackers inside; reaches: nothing beyond its own data; since at latest 2026-09-03; sends data: no network observed.
- Low: Spotify (com.spotify.music): trackers inside; reaches: nothing beyond its own data; since at latest 2026-09-06; sends data: no network observed.
- Low: Mega Slots (com.mega.slots.casino): adware heavy, from a store; reaches: nothing beyond its own data; since at latest 2026-09-04; sends data: no network observed.

## Deep control
Nothing flagged.

## Permissions
Nothing flagged.

## Persistence
Nothing flagged.

## Network
Nothing flagged.

## Verdict
VERDICT: clean

## Safe
- com.cleaner.turbo: installed outside a store, no sensitive grants
- com.free.coin.game: trackers inside
- com.spotify.music: trackers inside
- com.mega.slots.casino: adware heavy, from a store
- 3 other apps had nothing flagged.

## Worth removing
- com.cleaner.turbo (medium): heavy with advertising or profiling trackers, not from a store

## Needs a professional
- none

## The single most important thing to do next
If you do not need com.cleaner.turbo, uninstall it. If you do, expect it to sell what you do inside it.

## What this scan cannot see

- Rooted or system privileged spyware that hides from the app inventory. An unprivileged app cannot detect it; a forensic examination can.
- Packages an OEM hides from the inventory. The report says when the inventory may be incomplete.
- Anything on the network side: your router, your carrier, a compromised Wi Fi, or a SIM swap.
- Your accounts: a shared Google or Samsung account, a forwarded mailbox, or a cloud backup someone else can open.
- A physical tracker (AirTag, SmartTag) or a second phone. Use the Unknown tracker alerts in Settings for those.
- What a work profile's administrator sees on the work side.
- Traffic from apps that only sent data while the monitor was not running.
- Name lookups by apps that bring their own encrypted DNS (some browsers, notably Chrome) and so never ask the phone's resolver. Their other traffic still counts in the per app byte totals.
