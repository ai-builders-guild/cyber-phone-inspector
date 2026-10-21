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
            }
        }
    }
}
