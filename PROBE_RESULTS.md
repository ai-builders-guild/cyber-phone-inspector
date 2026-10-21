# PROBE_RESULTS.md — Phase 2 data source verification

Sanitized by construction: counts, field presence, timing and exception classes only. No package names from any device. Produced by the debug only `ProbeActivity` (see `app/src/debug/`). Each row maps to a data source in PROJECT.md.

How to read: Count is what the API returned; Field present is whether the field the collector needs was populated; Error is the exception class when the call threw. `count 1 = ...` notes explain boolean probes.

## Rick's phone

PENDING. The phone was not visible to the Mac over USB during this session (no adb device, nothing on the USB bus). Rerun: install the debug build, grant usage access in Settings, tap "Request VPN consent", then from the Mac run `adb shell "echo hi | toybox nc ::1 27077"` within 20 s, then tap "Run probes" and pull `files/probe_report.md` with `adb shell run-as com.cyberrick.phoneinspector cat files/probe_report.md`.

## Emulators

### Google sdk_gphone64_arm64, Android 16 (API 36)

| API | Count | Field present | Elapsed ms | Error | Note |
|---|---|---|---|---|---|
| PackageManager.getInstalledPackages | 256 | yes | 215 | - | - |
| PackageManager.getInstallSourceInfo | 23 | yes | 189 | - | count = packages with a known installer |
| PackageManager.GET_SIGNING_CERTIFICATES | 256 | yes | 543 | - | count = packages with signingInfo |
| PackageManager.queryIntentActivities(MAIN,LAUNCHER) | 23 | yes | 8 | - | - |
| DevicePolicyManager.getActiveAdmins | 0 | no | 0 | - | null list is recorded as count 0, field present no |
| Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES | 0 | no | 1 | - | count = colon separated entries |
| AccessibilityManager.getEnabledAccessibilityServiceList | 0 | yes | 0 | - | - |
| Settings.Secure.enabled_notification_listeners | 2 | yes | 0 | - | count = colon separated entries |
| PackageInfo.requestedPermissionsFlags | 135 | yes | 21 | - | count = packages where flags are non null |
| ApplicationInfo.category | 25 | yes | 4 | - | count = packages with a defined category |
| PackageManager.queryBroadcastReceivers(BOOT_COMPLETED) | 129 | yes | 4 | - | - |
| AppOpsManager GET_USAGE_STATS for this app | 1 | yes | 0 | - | count 1 = granted |
| UsageStatsManager.queryEvents last 24h | 143 | yes | 1 | - | - |
| UsageStatsManager.queryUsageStats last 7d | 60 | yes | 0 | - | - |
| NetworkStatsManager.querySummary(WIFI) last 24h | 33 | yes | 11 | - | count = uid buckets |
| ConnectivityManager.getConnectionOwnerUid(UDP) | 0 | no | 2 | - | count 1 = resolved to own uid on a connected but silent UDP socket |
| ConnectivityManager.getConnectionOwnerUid(TCP loopback) | 0 | no | 0 | - | count 1 = resolved to own uid on a real loopback connection |
| LinkProperties.isPrivateDnsActive | error | - | - | java.lang.SecurityException | count 1 = private DNS active on the default network |
| VpnService.prepare | 1 | yes | 0 | - | count 1 = consent already granted, 0 = consent dialog needed |
| VpnService.Builder.establish | 1 | yes | 36 | - | count 1 = tun opened |
| ConnectivityManager.getConnectionOwnerUid(TCP from another uid) while VPN active | 1 | yes | 4004 | - | count 1 = resolved to a uid other than our own; peer was the adb shell |
| NetworkCapabilities.TRANSPORT_VPN after probe | error | - | - | java.lang.SecurityException | - |

### Google Android SDK built for arm64, Android 10 (API 29)

| API | Count | Field present | Elapsed ms | Error | Note |
|---|---|---|---|---|---|
| PackageManager.getInstalledPackages | 175 | yes | 15 | - | - |
| PackageManager.getInstallSourceInfo | 0 | no | 34 | - | count = packages with a known installer |
| PackageManager.GET_SIGNING_CERTIFICATES | 175 | yes | 46 | - | count = packages with signingInfo |
| PackageManager.queryIntentActivities(MAIN,LAUNCHER) | 20 | yes | 0 | - | - |
| DevicePolicyManager.getActiveAdmins | 0 | no | 0 | - | null list is recorded as count 0, field present no |
| Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES | 0 | no | 0 | - | count = colon separated entries |
| AccessibilityManager.getEnabledAccessibilityServiceList | 0 | yes | 0 | - | - |
| Settings.Secure.enabled_notification_listeners | 2 | yes | 1 | - | count = colon separated entries |
| PackageInfo.requestedPermissionsFlags | 98 | yes | 10 | - | count = packages where flags are non null |
| ApplicationInfo.category | 10 | yes | 3 | - | count = packages with a defined category |
| PackageManager.queryBroadcastReceivers(BOOT_COMPLETED) | 79 | yes | 2 | - | - |
| AppOpsManager GET_USAGE_STATS for this app | 1 | yes | 0 | - | count 1 = granted |
| UsageStatsManager.queryEvents last 24h | 96 | yes | 0 | - | - |
| UsageStatsManager.queryUsageStats last 7d | 56 | yes | 0 | - | - |
| NetworkStatsManager.querySummary(WIFI) last 24h | 10 | yes | 47 | - | count = uid buckets |
| ConnectivityManager.getConnectionOwnerUid(UDP) | error | - | - | java.lang.SecurityException | count 1 = resolved to own uid on a connected but silent UDP socket |
| ConnectivityManager.getConnectionOwnerUid(TCP loopback) | error | - | - | java.lang.SecurityException | count 1 = resolved to own uid on a real loopback connection |
| LinkProperties.isPrivateDnsActive | error | - | - | java.lang.SecurityException | count 1 = private DNS active on the default network |
| VpnService.prepare | 1 | yes | 21 | - | count 1 = consent already granted, 0 = consent dialog needed |
| VpnService.Builder.establish | 1 | yes | 57 | - | count 1 = tun opened |
| ConnectivityManager.getConnectionOwnerUid(TCP from another uid) while VPN active | 1 | yes | 4033 | - | count 1 = resolved to a uid other than our own; peer was the adb shell |
| NetworkCapabilities.TRANSPORT_VPN after probe | error | - | - | java.lang.SecurityException | - |

## Findings that change the plan

1. **`ConnectivityManager.getConnectionOwnerUid` only resolves other apps' flows, and only while this app is the active VPN.** Outside the VPN it throws SecurityException on API 29 and returns INVALID_UID on API 36. Inside the VPN, a lookup of the VPN app's own socket also returns INVALID_UID because the VPN app's traffic is excluded from its own tunnel. A connection from a different uid (the adb shell) resolved correctly on both API 29 and API 36. Phase 11 design holds: attribute flows inside the tunnel service, never from the activity, and never expect to see our own traffic.
2. **Every `ConnectivityManager` read (`getActiveNetwork`, `getLinkProperties`, `getNetworkCapabilities`) needs `ACCESS_NETWORK_STATE`**, a normal permission that is not on the PROJECT.md allowlist. Without it: SecurityException on both API levels. Phase 11 needs it for private DNS detection and for confirming the tunnel is torn down. Decision pending with Rick (stopping condition in PROJECT.md).
3. **`getInstallSourceInfo` is API 30+.** On API 29 the fallback `getInstallerPackageName` returned no installer for any package on the emulator, which has no Play Store; expected, but the Apps collector must treat "no installer" as `unknown`, not as sideloaded, on API 29 devices without further evidence.
4. **`getActiveAdmins` returns null when there are no admins** (both API levels). Collector treats null as empty.
5. **`ENABLED_ACCESSIBILITY_SERVICES` is null when no service is enabled** (both API levels), not an empty string. Collector treats null as empty.
6. **`requestedPermissionsFlags` is null for roughly half of packages** (packages that request nothing). Collector must not read flags for those.
7. **`UsageStatsManager` and `NetworkStatsManager` both return data once usage access is granted**, on both API levels, with sub 50 ms latency. `NetworkStatsManager.querySummary(TYPE_WIFI, null, ...)` works without a subscriber id.
8. **`VpnService.Builder.establish` works on both API levels with a single /32 route**, the tun closes cleanly, and the emulator shows no VPN key icon afterwards (checked by screenshot on API 36). Programmatic confirmation needs finding 2.
9. **Performance**: full package inventory with permissions and signing certificates under 70 ms for 256 packages; per package `getInstallSourceInfo` is the slow call at 630 ms for 256 packages (2.5 ms each). Fine for the collector but keep it off the main thread.
10. **Socket probes must run off the main thread** (NetworkOnMainThreadException on API 36). Collectors run on `Dispatchers.IO` by rule.
