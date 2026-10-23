# Using Cyber Phone Inspector

A plain guide for the person holding the phone. Nothing in this app changes your phone on its own. It reads, it tells you what it found, and when you choose to remove an app it hands you to Android's own uninstall confirmation.

## 1. Install

You need an Android phone on Android 10 or newer.

1. Download `app-release.apk` from the latest release: https://github.com/ai-builders-guild/cyber-phone-inspector/releases
2. Open the downloaded file. Android asks whether to allow installs from this source (your browser or Files app); allow it once, then tap Install.
3. The app asks for no permissions at install time beyond what Android grants every app. Two optional ones come later, and both are explained on screen before Android asks.

Updating: install the newer APK over the old one. Your kept list and the lists on the phone stay.

## 2. The first scan

1. Open the app. You see an empty ring, the question "Is anything stealing from this phone?", and **Scan now**.
2. The first time, a sheet called **Before the first scan** explains what the scan reads and names the lists it checks against. Tap **Continue and scan**.
3. Android asks whether the app may show notifications (for the progress bar while scanning). Either answer works.
4. The ring fills as five checks tick off: apps and what is inside them, who has deep control, what can hear or watch you, what runs all the time, where your data goes.

The first scan on a build reads every app once and can take one to three minutes on a phone with many large games. Later scans take under thirty seconds.

**Usage access (optional).** The "what runs all the time" check needs Android's usage access. If you have not granted it, the scanning screen offers a button that opens the right Settings page; the scan continues without it and the result says one check could not run.

## 3. Reading the result

| On screen | What it means |
|---|---|
| **Safety score** (the ring) | 100 is nothing found. Each app with a high finding costs 30, each app whose worst finding is medium costs 10, notes cost nothing. If a check could not run the score stops at 90 and says why. |
| **Headline** | "N threats found" (apps with a high finding), "N things to review" (apps whose worst is medium), or "Nothing found". |
| **Cards** | One per flagged app, worst first. Each has a label, the app's name and one sentence. Tap a card for the detail. |
| **Remove the threat(s)** | Shown when there are highs. Walks every high through Android's uninstall dialog, one after another. |
| **Watch where your data goes** | An optional 10 minute watch (see 6). |
| **N apps are fine** | Everything with nothing above a note. "See the full report" opens the professional's view. |
| **Lists dated …** | The age of the threat lists on this phone (see 7). |

### The labels

| Label | What was found |
|---|---|
| **Known malware** | The app's installed file is byte for byte a sample on the MalwareBazaar malware list. |
| **Steals data** | The app is on a published spyware or stalkerware list, or it contacted a server on a spyware, URLhaus or ThreatFox list. |
| **Hidden** | No launcher icon and not from a store, or named like a part of Android but not one, while holding deep access. |
| **Reads your screen** | Holds an accessibility service, which can read everything on screen and tap for you, and is not an accessibility app. |
| **Can wipe this phone** | Holds device admin rights without managing a work profile. |
| **Reads your messages** | Holds notification access and did not come from a store. |
| **Adware heavy** | Installed outside a store and stuffed with advertising or profiling trackers. |
| **Review** | A medium worth a look: an odd permission on a non store app, a fixed schedule beacon to an unrecognised host, and so on. |
| **Note** | Lows. Not shown as cards; listed in the full report. |

High and medium are also written out on every card and detail, never carried by colour alone.

## 4. The detail screen

Tap a card. You see the label and severity, the app's name and package id, one sentence, then four facts:

- **Can read**: the sensitive things it holds (screen, notifications, device controls, location, microphone, camera, trackers inside).
- **Sends to**: the names it looked up during the watch, or "not watched yet".
- **On this phone since**: the earliest date the phone knows for it.
- **Runs**: at boot, hours in the background this week, or only when you open it.

Below that, **Why it is flagged** lists every rule that fired with its reason. Then two choices.

### Remove this app

Tap it and Android shows its own **Uninstall this app?** dialog. Nothing is removed until you tap Uninstall there. Cancel brings you back with "Still installed".

After an uninstall the app rescans on its own. If that was the last high, you land on **Threats are gone** with a short checklist of what to protect next (passwords from another device, two step sign in, statements, sign in alerts, your bank). Tick what you have done; the ticks stay on this phone only.

**Two steps to remove it.** An app that holds device admin, accessibility or notification access cannot be uninstalled while that control is on. The detail shows a two step card: **Open Settings** takes you to the page where you switch it off, and when you come back the **Uninstall** step unlocks.

**System apps** cannot be uninstalled. The detail says so and offers the app's Settings page, where it can be disabled.

### Keep it, I know this app

For an app you installed on purpose (a family locator, a remote access tool, an anti theft app). Tap it and the card disappears, the score goes up, and the app is listed under **Apps you marked as yours** with the date. It stays in the full report as a note; nothing is hidden.

If a kept app later gets a new or stronger finding it comes back as a card, and its detail offers **Keep it, including this** or **Stop keeping this app**.

## 5. The full report

**See the full report** opens the professional's view: the verdict line, every check, every flag with its five fields, what the scan touched (including the lists and their dates, and any kept app), the three lists (Safe, Worth removing, Needs a professional), the single most important next step, and what this scan cannot see. **Share report** sends the report and the evidence file; **Save to Downloads** writes both under `Download/CyberPhoneInspector`.

## 6. Watch where your data goes

An optional 10 minute watch. Tap **Start the 10 minute watch**; Android shows its VPN connection request (once per app); tap OK. Use the phone normally. The app runs a local DNS only VPN that notes which names each app looks up, nothing else: no traffic is redirected or stored, and the phone stays online. When it ends the app rescans on its own and the ring's dotted segment fills.

If your phone uses a named Private DNS provider the watch cannot see lookups and says so; set Private DNS to Automatic for the ten minutes if you want the watch.

## 7. Threat lists on this phone

Every check runs against lists stored on the phone. Nothing about your phone is sent anywhere. Tap **Lists dated …** to see the four lists and their dates, and **Refresh the lists now** to download newer copies from the project's public list repository. A download that does not parse or shrank badly is refused and the previous copy kept. **Refresh every week on Wi‑Fi** is off by default; switch it on if you want the lists to keep themselves fresh.

What the lists are and where they come from: [DATA_SOURCES.md](DATA_SOURCES.md).

## 8. What this app cannot see

- Rooted or system privileged spyware that hides from the app list. An ordinary app cannot detect it; a forensic examination can.
- Packages an OEM hides from the app list.
- Anything on the network side: your router, your carrier, a compromised Wi‑Fi, a SIM swap.
- Anything that happened before the scan: what was already taken.

If you believe someone you know installed something on this phone, do not remove it yet. Keep the phone as it is and get help from a digital security professional or a domestic violence support service; the report is written so a professional can read it.

## 9. Privacy

The app has no account, no analytics and no backend. The only connection it ever makes is the list refresh to the public feeds repository, and only when you tap the button or switch on the weekly refresh. Reports leave the phone only when you share or save them.
