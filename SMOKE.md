# SMOKE.md — manual smoke checklist on a real phone

Run by Rick on the Galaxy S24 Ultra before any release. Tick with the date and build. Nothing here writes to the phone except the app's own data.

Build: ______ Date: ______ Phone: ______ Android: ______

Install
- [ ] `adb install -r dist/app-release.apk` succeeds over the previous build (no uninstall, data kept).
- [ ] Launcher shows exactly one entry, Cyber Phone Inspector (no CPI Probe in a release build).

First run
- [ ] First launch shows "Before the first scan"; Continue leads to Home.
- [ ] Home shows the usage access status correctly before and after granting it in Settings.

Scan
- [ ] Start scan asks for notification permission once; both Allow and Don't allow run the scan.
- [ ] Scan finishes in under 20 s; the report screen shows the VERDICT line first.
- [ ] Rotate the phone during a scan: the scan continues, no restart.
- [ ] Save to Downloads creates two files under Download/CyberPhoneInspector and the toast says so.
- [ ] Share report opens the share sheet with two attachments.

Network monitor
- [ ] Start network monitor shows Android's Connection request dialog; OK starts it; the status line counts lookups.
- [ ] Phone stays online during the monitor (open a site, send a message).
- [ ] Monitor stops itself after 10 min; "Last run" shows; no VPN key icon in the status bar afterwards.
- [ ] A scan after the monitor shows the Network section with hosts per app.
- [ ] Private DNS set to a named provider: Start network monitor shows "Could not start" with the Private DNS message; app stays alive. Set it back to Automatic.

Report sanity
- [ ] Every flagged item names an app you can find in Settings > Apps.
- [ ] Nothing in the report is a package the phone does not have.
- [ ] The "What this scan touched" section matches what you granted.

Teardown
- [ ] Settings > Connections > More > VPN shows nothing running.
- [ ] Revoking usage access makes the next report say NOT CHECKED for Persistence.
