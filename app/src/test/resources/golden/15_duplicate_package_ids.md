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

### WhatsApp (com.whatsapp)
- What it is: com.whatsapp appears twice in the inventory, usually a leftover from a second user or a failed update.
- What it can reach: nothing beyond its own data
- How long it has been there: since at latest 2026-09-06
- Where it sends data: no network observed
- Severity: low
- What to do: Nothing to do. Mention it if you talk to a professional.

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
- com.whatsapp: com.whatsapp appears twice in the inventory, usually a leftover from a second user or a failed update.
- 5 other apps had nothing flagged.

## Worth removing
- none

## Needs a professional
- none

## The single most important thing to do next
Nothing to do. Mention it if you talk to a professional.

## What this scan cannot see

- Rooted or system privileged spyware that hides from the app inventory. An unprivileged app cannot detect it; a forensic examination can.
- Packages an OEM hides from the inventory. The report says when the inventory may be incomplete.
- Anything on the network side: your router, your carrier, a compromised Wi Fi, or a SIM swap.
- Your accounts: a shared Google or Samsung account, a forwarded mailbox, or a cloud backup someone else can open.
- A physical tracker (AirTag, SmartTag) or a second phone. Use the Unknown tracker alerts in Settings for those.
- What a work profile's administrator sees on the work side.
- Traffic from apps that only sent data while the monitor was not running.
- Name lookups by apps that bring their own encrypted DNS (some browsers, notably Chrome) and so never ask the phone's resolver. Their other traffic still counts in the per app byte totals.
