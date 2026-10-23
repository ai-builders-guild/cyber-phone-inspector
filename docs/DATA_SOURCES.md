# Where the data comes from

Cyber Phone Inspector decides what is a threat in two ways: **published lists** it checks your apps against, and **behaviour rules** that look at what an app is and holds. Both run on the phone. Nothing about your phone is sent to any list provider or to us.

## The four lists on the phone

The lists ship inside the app (`app/src/main/assets/feeds/`) and are refreshed from the public data repository https://github.com/CyberRick-AI/cyber-phone-inspector-feeds, which a GitHub Action rebuilds every week with `tools/fetch_feeds.py`. The app downloads only from that repository's raw address, over HTTPS, and refuses a download that does not parse as its list or that shrank by more than half.

| List | File | Source | What it catches | Size (2026-10-23) | Licence |
|---|---|---|---|---|---|
| Spyware and stalkerware | `spyware.json` | The MVT indicator index (Mobile Verification Toolkit, by Amnesty International's Security Lab) and the lists it names | Known spyware and stalkerware by package id, signing certificate, domain and IP | 680 packages, 6,548 domains, 497 certificates, 73 IPs | Each list's own terms (see below) |
| Trackers | `trackers.json` | Exodus Privacy | Advertising, analytics, profiling, location and identification kits inside an app's code, by class name signature | 428 trackers | CC BY-SA 4.0 |
| Malware hashes | `malware_hashes.json` | MalwareBazaar (abuse.ch) | Known Android malware samples by SHA-256 of the APK, with the family and tags | 1,941 hashes | CC0 |
| Malicious hosts | `malicious_hosts.json` | URLhaus and ThreatFox (abuse.ch) | Domains and IPs that spread malware or receive stolen data | 3,204 domains, 1,086 IPs | CC0 |

### The MVT index in detail

The spyware list is built from the indicator index that MVT publishes at https://github.com/mvt-project/mvt-indicators. At the time of writing it names these lists, each a STIX2 file:

- Amnesty International Security Lab investigations: NSO Group Pegasus (2021), an Android campaign (2023), Wintego Helios (2024), NoviSpy in Serbia (2024).
- MVT indicators: Intellexa Predator, RCS Lab, Quadream KingsPawn, Operation Triangulation, WyrmSpy and DragonEgg, EagleMsgSpy, Candiru, ResidentBat, Cellebrite, DarkSword, Coruna, Morpheus, BTMob, Spyrtacus.
- Echap's stalkerware indicators (https://github.com/AssoEchap/stalkerware-indicators): the consumer "monitoring" apps sold to watch partners and family members.

Some of those lists deliberately include dual use tools, such as family locators and remote access apps. That is why the app reads an openly installed, store sourced app on a list as **Review** (medium) and reserves **Steals data** (high) for one that is hidden, disguised or installed outside a store, and why **Keep it, I know this app** exists.

### What each list does in the app

| List | Rule(s) | Severity |
|---|---|---|
| Spyware | `ioc_match` (package id or signing certificate), `ioc_domain_contact` (a looked up name during the watch) | High; medium when the app is visible and from a store |
| Malware hashes | `known_malware_hash` | High |
| Malicious hosts | `malicious_host_contact` | High |
| Trackers | `adware_heavy` (five or more advertising trackers, or a profiling tracker, in an app installed outside a store), `adware_heavy_store` (ten or more advertising trackers in a store app), `trackers_note` | Medium; note; note |

## Behaviour rules (no list needed)

The rest of the 29 rules in `app/src/main/assets/rules.json` look at what an app is and holds. They never call anything malicious; the words are "hidden", "unrecognised", "odd" and the detail says exactly what was seen.

| What is looked at | Examples of rules |
|---|---|
| Hidden launcher, disguised system name, install outside a store | `hidden_nonstore_with_power`, `disguised_system_name`, `sideloaded_with_sensitive` |
| Deep control: device admin, accessibility, notification access, device or profile owner | `accessibility_non_system`, `device_admin_no_profile`, `notification_listener_nonstore` |
| Microphone, camera, location and background location grants that look odd for the app's category | `unexpected_grant_nonstore`, `unexpected_grant_store` |
| Runs at boot and for hours in the background (needs usage access) | `boot_and_background_heavy`, `background_heavy_alone` |
| Where data goes during the 10 minute watch: fixed schedule beacons to unrecognised hosts, bytes sent with no name lookup | `beacon_unrecognized_host`, `raw_ip_suspect`, `unrecognized_host_with_oddity` |

"Unrecognised host" means a name that is not on the app's short list of well known services (`known_services.json`), not a judgement about the host.

## How the checks run

1. The app inventory, deep control, permissions and persistence collectors read Android's own records.
2. For every non system app, the installed APK is hashed (SHA-256) and its code is searched for the tracker signatures. Both results are cached per app version, so a rescan only reads apps that changed.
3. The rule engine evaluates every rule against every app, with the lists as its threat intelligence.
4. Apps you kept are collapsed to a note; the report, the score and the cards are built from the result.

## Refreshing

- **On the phone:** Lists screen → Refresh the lists now; or switch on "Refresh every week on Wi‑Fi".
- **In the data repository:** the Action runs every Monday and on demand; it holds the only abuse.ch key as a repository secret. Testers never need a key.
- **For a new build:** copy the repository's `feeds/` folder into `app/src/main/assets/feeds/`.

## Attribution and licences

- Exodus Privacy tracker data is licensed CC BY-SA 4.0 and is credited in the app's first run sheet, in `NOTICE` and in `FEEDS.md`.
- abuse.ch data (MalwareBazaar, URLhaus, ThreatFox) is CC0.
- MVT indicators carry the terms of the lists the index names.
- The lists are data; the program is GPL-3.0-or-later.

## What no list can tell you

A list match says "this file, package or host is on a list"; it does not know why the app is on your phone. A clean result says "nothing matched and nothing looked odd", not "the phone is clean". See "What this app cannot see" in [USING.md](USING.md).
