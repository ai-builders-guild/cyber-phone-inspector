package com.cyberrick.phoneinspector.rules

import com.cyberrick.phoneinspector.core.model.ControlKind
import com.cyberrick.phoneinspector.core.model.Evidence
import com.cyberrick.phoneinspector.core.model.InstallSource
import com.cyberrick.phoneinspector.core.model.SensitiveGroup

/** Flattens the evidence into one fact map per package. Fact names are the vocabulary rules.json may use. */
object Facts {
    val STORE_SOURCES = setOf(InstallSource.PLAY_STORE, InstallSource.VENDOR_STORE, InstallSource.WEB_APP)

    fun perPackage(
        evidence: Evidence,
        ioc: IocMatcher,
        known: KnownServices = KnownServices(emptySet()),
    ): Map<String, Map<String, Any?>> {
        val packages =
            (
                evidence.apps.map { it.packageName } + evidence.controls.map { it.packageName } +
                    evidence.permissions.filter { it.granted }.map { it.packageName } +
                    evidence.persistence.filter { it.bootReceiver || it.backgroundHeavy }.map { it.packageName } +
                    evidence.flows.mapNotNull { it.packageName }
            ).toSortedSet()
        val appsByPackage = evidence.apps.groupBy { it.packageName }
        val controlsByPackage = evidence.controls.groupBy { it.packageName }
        val permsByPackage = evidence.permissions.groupBy { it.packageName }
        val persistByPackage = evidence.persistence.associateBy { it.packageName }
        val flowsByPackage = evidence.flows.groupBy { it.packageName }
        return packages.associateWith { pkg ->
            val app = appsByPackage[pkg]?.firstOrNull()
            val controls = controlsByPackage[pkg].orEmpty().map { it.kind }.toSet()
            val perms = permsByPackage[pkg].orEmpty()
            val persist = persistByPackage[pkg]
            val hosts = flowsByPackage[pkg].orEmpty().mapNotNull { it.host }
            buildMap {
                put("packageName", pkg)
                put("inInventory", app != null)
                put("installSource", app?.installSource?.name)
                put("storeInstall", app?.installSource in STORE_SOURCES)
                // Covertness separates stalkerware from an openly installed monitoring app: store install, icon, honest name.
                put(
                    "visibleStoreInstall",
                    app != null && app.installSource in STORE_SOURCES && app.hasLauncherIcon && !app.hidden && !app.disguised,
                )
                put("isSystem", app?.isSystem ?: false)
                put("hidden", app?.hidden ?: false)
                put("disguised", app?.disguised ?: false)
                put("duplicate", app?.duplicate ?: false)
                put("category", app?.category)
                ControlKind.entries.forEach { put("control.${it.name}", it in controls) }
                put("anyControl", controls.isNotEmpty())
                SensitiveGroup.entries.forEach { g ->
                    put("granted.${g.name}", perms.any { it.group == g && it.granted })
                    put("unexpected.${g.name}", perms.any { it.group == g && it.unexpected })
                }
                put("anySensitiveGranted", perms.any { it.granted })
                put("anyUnexpectedGranted", perms.any { it.unexpected })
                put("bootReceiver", persist?.bootReceiver ?: false)
                put("backgroundHeavy", persist?.backgroundHeavy ?: false)
                put("foregroundServiceMinutes7d", persist?.foregroundServiceMinutes7d ?: 0L)
                put("iocPackage", ioc.packageHit(pkg))
                put("iocCert", app?.let { ioc.certHit(it.signingCertSha256) } ?: false)
                put("iocDomain", hosts.any { ioc.domainHit(it) })
                val net = NetworkFacts.forPackage(pkg, evidence.flows, known)
                put("net.knownHosts", net.knownHosts.size)
                put("net.unrecognizedHosts", net.unrecognizedHosts.size)
                put("net.beacon", net.beacon != null)
                put("net.beaconHost", net.beacon?.host ?: "")
                put("net.beaconInterval", net.beacon?.intervalSeconds ?: 0)
                put("net.rawIpSuspect", net.rawIpSuspect)
                put("net.bytesSentMb", net.bytesSent / 1_048_576)
                put("net.unrecognizedList", net.unrecognizedHosts.take(5).joinToString(", "))
                // Any other oddity on the app turns an unrecognized host from low into medium.
                put(
                    "anyOddity",
                    (app?.installSource !in STORE_SOURCES && app != null) ||
                        controls.isNotEmpty() ||
                        perms.any { it.unexpected } ||
                        (app?.hidden ?: false) ||
                        (app?.disguised ?: false) ||
                        (persist?.backgroundHeavy ?: false),
                )
                val sources = ioc.sourcesFor(pkg)
                put(
                    "iocSources",
                    if (sources.isNotEmpty()) {
                        sources.joinToString(", ")
                    } else if (app != null &&
                        ioc.certHit(app.signingCertSha256)
                    ) {
                        "signing certificate match"
                    } else {
                        ""
                    },
                )
            }
        }
    }
}
