# Security policy

## What this app does with your data

Nothing leaves the phone except what you share or save yourself. The app has no account, no analytics and no backend. Its only network connection is the list refresh from https://github.com/CyberRick-AI/cyber-phone-inspector-feeds, over HTTPS, when you ask for it. It reads the phone; it changes nothing; removal goes through Android's own uninstall confirmation.

## Reporting a vulnerability

If you find a way the app could leak data, change the phone without the person's action, be made to contact another host, or be tricked by a crafted app or list, please report it privately rather than in a public issue.

- Open a private report at https://github.com/ai-builders-guild/cyber-phone-inspector/security/advisories/new, or
- email rick@cyberrick.ai with "Cyber Phone Inspector" in the subject.

Include the version (Settings > Apps > Cyber Phone Inspector, or the release tag), the Android version, and steps or a sample. You will get an acknowledgement within a week. Please give us a reasonable time to fix before disclosing.

## Scope

In scope: the app, the feeds script and the feeds repository's workflow.
Out of scope: the upstream lists' contents (report false entries to MVT, Exodus Privacy or abuse.ch), and Android itself.

## Supported versions

The latest release tag only.
