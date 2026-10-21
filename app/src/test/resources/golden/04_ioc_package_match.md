# Cyber Phone Inspector report

Device: Google Pixel 9, Android 16 (API 36)
Scan time: 2026-10-21T12:00:00Z (UTC), 2026-10-21 08:00 EDT (local)
Indicator snapshot: 2026-10-21
Read only: this scan changed nothing on the phone.

## What this scan touched
- Read the installed app list, permissions, device admin, accessibility and notification settings. No consent needed; nothing changed.
- Usage access: read which apps ran foreground services in the last 7 days. You granted this; revoke it any time in Settings.
- Local VPN: observed DNS lookups for the monitor window. You granted this; it stopped when the scan ended. No traffic was redirected or stored.

## Apps

### Battery Saver (com.example.ioc.match)
- What it is: com.example.ioc.match matches a published indicator of stalkerware or spyware (listed by: Test only list). Some lists also include dual use tools such as remote access or family monitoring apps; the list name tells you which.
- What it can reach: nothing beyond its own data
- How long it has been there: since at latest 2026-09-09
- Where it sends data: no network observed
- Severity: high
- What to do: Do not uninstall yet. Preserve the phone as evidence and contact a digital security professional or a domestic violence support service.

### Battery Saver (com.example.ioc.match)
- What it is: com.example.ioc.match was installed outside an app store but holds no microphone, camera or location grant.
- What it can reach: nothing beyond its own data
- How long it has been there: since at latest 2026-09-09
- Where it sends data: no network observed
- Severity: low
- What to do: Nothing to do if you installed it on purpose.

## Deep control
Nothing flagged.

## Permissions
Nothing flagged.

## Persistence
Nothing flagged.

## Network
Nothing flagged.

## Verdict
VERDICT: compromised

## Safe
- com.example.ioc.match: com.example.ioc.match was installed outside an app store but holds no microphone, camera or location grant.
- 8 other apps had nothing flagged.

## Worth removing
- none

## Needs a professional
- com.example.ioc.match (high): com.example.ioc.match matches a published indicator of stalkerware or spyware (listed by: Test only list). Some lists also include dual use tools such as remote access or family monitoring apps; the list name tells you which.

## The single most important thing to do next
Do not uninstall yet. Preserve the phone as evidence and contact a digital security professional or a domestic violence support service.

## What this scan cannot see

- Rooted or system privileged spyware that hides from the app inventory. An unprivileged app cannot detect it; a forensic examination can.
- Packages an OEM hides from the inventory. The report says when the inventory may be incomplete.
- Anything on the network side: your router, your carrier, a compromised Wi Fi, or a SIM swap.
- Your accounts: a shared Google or Samsung account, a forwarded mailbox, or a cloud backup someone else can open.
- A physical tracker (AirTag, SmartTag) or a second phone. Use the Unknown tracker alerts in Settings for those.
- What a work profile's administrator sees on the work side.
- Traffic from apps that only sent data while the monitor was not running.
