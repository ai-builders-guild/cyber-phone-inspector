# SMOKE.md — manual smoke checklist on a real phone (v1.1)

Run by Rick on the Galaxy S24 Ultra before any release. Tick with the date and build. Nothing here writes to the phone except the app's own data and, where you choose it, Android's own uninstall of an app you picked.

Build: 0.2.0 (commit 39db94d, includes the keep again fix) Date: 2026-10-22 Phone: Galaxy S24 Ultra Android: 16

Install
- [x] `adb install -r dist/app-release.apk` succeeds over the previous build (no uninstall, data kept).
- [x] Launcher shows exactly one entry, Cyber Phone Inspector.

First run
- [x] Launch shows the empty ring, "Is anything stealing from this phone?" and Scan now.
- [x] The first Scan now shows "Before the first scan"; Continue and scan starts the scan (the sheet never shows again; "What a scan touches" reopens it).

Scan
- [x] Notification permission is asked once; both Allow and Don't allow run the scan.
- [x] The first scan on this build finishes in under 3 minutes (it reads every app once); the second in under 30 s.
- [x] Rotate the phone during a scan and on the result: the scan continues, the result stays.
- [x] The result shows the score ring, one headline, cards worst first, "N apps are fine" and "Lists dated <date>".
- [x] Every card names an app you can find in Settings > Apps; tap one: the detail shows four facts and the reasons.

Keep
- [x] On an app you installed yourself, "Keep it, I know this app" removes the card at once, the score goes up and "1 kept" shows.
- [x] "Apps you marked as yours" lists it with the date; "Stop keeping this app" brings the card back.
- [x] The full report lists the kept app under "What this scan touched" while it is kept.

Remove (use an app you installed and do not need; nothing else)
- [x] Remove this app shows Android's own "Uninstall this app?" dialog; Cancel returns with "Still installed".
- [x] Uninstall there: the app rescans on its own; the card is gone; if it was the last high, the After screen shows the checklist.
- [x] An app holding device admin or accessibility shows the two step card; Open Settings lands on the right page; after switching it off and coming back, the Uninstall step unlocks.

Lists
- [x] "Lists dated <date>" opens the lists screen with four lists and their dates.
- [x] Refresh the lists now finishes with "Lists are up to date" or "Updated: …" and no error; the dates do not go backwards.
- [x] Airplane mode on, refresh: it fails with a message, the dates stay, the app stays alive. Airplane mode off.
- [x] The weekly switch stays as set after closing and reopening the app.

Data flow watch
- [x] Start the 10 minute watch shows Android's Connection request dialog; OK starts it; the card counts lookups.
- [x] Phone stays online during the watch (open a site, send a message).
- [x] The watch stops itself after 10 min and the app rescans on its own; the ring's dotted segment fills; no VPN key icon afterwards.
- [x] Private DNS set to a named provider: the watch shows "Could not start" with the Private DNS message. Set it back to Automatic.

Report
- [x] See the full report shows VERDICT first; Share opens the share sheet with two attachments; Save to Downloads creates two files under Download/CyberPhoneInspector.
- [x] Nothing in the report is a package the phone does not have.
- [x] "What this scan touched" matches what you granted and names the lists on the phone.

Teardown
- [x] Settings > Connections > More > VPN shows nothing running.
- [x] Revoking usage access makes the next result say a check could not run, capped at 90, with the Grant usage access button.

Result 2026-10-22: all items passed (Rick). One finding during the walk, fixed before the end of it: a kept app that returned with a new finding could not be kept again; now it can.
