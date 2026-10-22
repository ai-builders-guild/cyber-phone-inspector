# Cyber Phone Inspector report

Device: samsung SM-S928U, Android 16 (API 36)
Scan time: 2026-10-21T12:00:00Z (UTC), 2026-10-21 08:00 EDT (local)
Indicator snapshot: 2026-10-21
Read only: this scan changed nothing on the phone.

## What this scan touched
- Read the installed app list, permissions, device admin, accessibility and notification settings. No consent needed; nothing changed.
- Usage access: read which apps ran foreground services in the last 7 days. You granted this; revoke it any time in Settings.
- Local VPN: observed DNS lookups for the monitor window. You granted this; it stopped when the scan ended. No traffic was redirected or stored.

## Apps

### Sync Service (com.sysconfig.service)
- What it is: com.sysconfig.service has no icon, did not come from an app store, and holds accessibility, device admin, or a microphone, camera or location grant. This is the shape of stalkerware.
- What it can reach: everything on screen, and it can tap and type for you, location in the background, microphone, starts at boot, ran 163 h in the background this week
- How long it has been there: since at latest 2026-09-01
- Where it sends data: upd.srv-telemetry.top
- Severity: high
- What to do: Remove it, then protect your accounts: change your email and bank passwords from another device, turn on two step sign in, and check your statements. If you believe someone you know installed it, do not remove it yet; keep the phone as it is and get help.

### Sync Service (com.sysconfig.service)
- What it is: com.sysconfig.service is named like a system component but is not one and did not come from an app store.
- What it can reach: everything on screen, and it can tap and type for you, location in the background, microphone, starts at boot, ran 163 h in the background this week
- How long it has been there: since at latest 2026-09-01
- Where it sends data: upd.srv-telemetry.top
- Severity: medium
- What to do: Uninstall com.sysconfig.service from Settings > Apps unless you know exactly what it is.

### Sync Service (com.sysconfig.service)
- What it is: com.sysconfig.service was installed outside an app store and holds a microphone, camera or location grant.
- What it can reach: everything on screen, and it can tap and type for you, location in the background, microphone, starts at boot, ran 163 h in the background this week
- How long it has been there: since at latest 2026-09-01
- Where it sends data: upd.srv-telemetry.top
- Severity: medium
- What to do: If you did not install com.sysconfig.service yourself, uninstall it. If you did, revoke the permissions it does not need.

## Deep control

### Sync Service (com.sysconfig.service)
- What it is: com.sysconfig.service is an enabled accessibility service and is not an accessibility app. Accessibility services can read everything on screen and type on your behalf.
- What it can reach: everything on screen, and it can tap and type for you, location in the background, microphone, starts at boot, ran 163 h in the background this week
- How long it has been there: since at latest 2026-09-01
- Where it sends data: upd.srv-telemetry.top
- Severity: high
- What to do: Remove it, then protect your accounts: change your email and bank passwords from another device, turn on two step sign in, and check your statements. If you believe someone you know installed it, do not remove it yet; keep the phone as it is and get help.

## Permissions

### Sync Service (com.sysconfig.service)
- What it is: com.sysconfig.service did not come from an app store and holds a microphone, camera or location grant that looks odd for this kind of app.
- What it can reach: everything on screen, and it can tap and type for you, location in the background, microphone, starts at boot, ran 163 h in the background this week
- How long it has been there: since at latest 2026-09-01
- Where it sends data: upd.srv-telemetry.top
- Severity: medium
- What to do: Revoke the permission in Settings > Apps > com.sysconfig.service > Permissions, or uninstall it.

## Persistence

### Sync Service (com.sysconfig.service)
- What it is: com.sysconfig.service starts itself at boot and ran in the background for more than 6 hours this week, and it is not a music, messaging, navigation or accessibility app.
- What it can reach: everything on screen, and it can tap and type for you, location in the background, microphone, starts at boot, ran 163 h in the background this week
- How long it has been there: since at latest 2026-09-01
- Where it sends data: upd.srv-telemetry.top
- Severity: medium
- What to do: Open Settings > Apps > com.sysconfig.service > Battery and check whether the activity makes sense. Uninstall if you do not use it.

## Network

### Sync Service (com.sysconfig.service)
- What it is: com.sysconfig.service looked up upd.srv-telemetry.top every 60 s for the whole monitor window. A fixed schedule to a host that is not a recognized service is how monitoring software reports in. It can also be an ordinary app polling its own server.
- What it can reach: everything on screen, and it can tap and type for you, location in the background, microphone, starts at boot, ran 163 h in the background this week
- How long it has been there: since at latest 2026-09-01
- Where it sends data: upd.srv-telemetry.top
- Severity: medium
- What to do: If you do not know what com.sysconfig.service is or why it would report on a schedule, uninstall it. If you do, nothing to do.

Where each app sent data during the monitor window:
- Spotify (com.spotify.music): known: api.spotify.com
- Sync Service (com.sysconfig.service): unrecognized: upd.srv-telemetry.top; every 60 s to upd.srv-telemetry.top (8 times)
- WhatsApp (com.whatsapp): known: g.whatsapp.net

## Verdict
VERDICT: compromised

## Safe
- 8 other apps had nothing flagged.

## Worth removing
- com.sysconfig.service (medium): reports to an unrecognized host on a fixed schedule; starts at boot and ran over 6 h this week; named like a system component, not one; installed outside a store, holds a sensitive grant; odd mic, camera or location grant, not from a store

## Needs a professional
- com.sysconfig.service (high): hidden, not from a store, holds deep access; accessibility service, not an accessibility app

## The single most important thing to do next
Remove it, then protect your accounts: change your email and bank passwords from another device, turn on two step sign in, and check your statements. If you believe someone you know installed it, do not remove it yet; keep the phone as it is and get help.

## What this scan cannot see

- Rooted or system privileged spyware that hides from the app inventory. An unprivileged app cannot detect it; a forensic examination can.
- Packages an OEM hides from the inventory. The report says when the inventory may be incomplete.
- Anything on the network side: your router, your carrier, a compromised Wi Fi, or a SIM swap.
- Your accounts: a shared Google or Samsung account, a forwarded mailbox, or a cloud backup someone else can open.
- A physical tracker (AirTag, SmartTag) or a second phone. Use the Unknown tracker alerts in Settings for those.
- What a work profile's administrator sees on the work side.
- Traffic from apps that only sent data while the monitor was not running.
- Name lookups by apps that bring their own encrypted DNS (some browsers, notably Chrome) and so never ask the phone's resolver. Their other traffic still counts in the per app byte totals.
