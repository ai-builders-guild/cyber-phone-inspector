package com.cyberrick.phoneinspector.collect

import com.cyberrick.phoneinspector.core.model.CheckKind
import com.cyberrick.phoneinspector.core.model.CheckResult
import com.cyberrick.phoneinspector.core.model.CheckStatus
import com.cyberrick.phoneinspector.core.model.PermissionRecord
import com.cyberrick.phoneinspector.core.model.SensitiveGroup

/**
 * Permissions check: for every app and each of the four sensitive groups, whether it is requested and granted,
 * and whether a grant looks unexpected for the app's category. "granted" is the permission flag, never usage.
 * The app label is never consulted.
 */
class PermissionsCollector(
    private val source: PermissionSource,
    private val expectations: PermissionExpectations,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun collect(): CollectorResult<PermissionRecord> {
        val collectedAt = clock()
        val packages =
            try {
                source.packagePermissions()
            } catch (e: Exception) {
                return CollectorResult(
                    emptyList(),
                    CheckResult(CheckKind.PERMISSIONS, CheckStatus.NOT_CHECKED, "${e.javaClass.simpleName}: ${e.message}"),
                )
            }
        val records =
            packages
                .sortedBy { it.packageName }
                .flatMap { pkg -> SensitiveGroup.entries.map { group -> record(pkg, group, collectedAt) } }
        return CollectorResult(records, CheckResult(CheckKind.PERMISSIONS, CheckStatus.CHECKED))
    }

    private fun record(
        pkg: RawPackagePermissions,
        group: SensitiveGroup,
        collectedAt: Long,
    ): PermissionRecord {
        val names = GROUP_PERMISSIONS.getValue(group)
        val indices =
            pkg.requestedPermissions
                .withIndex()
                .filter { it.value in names }
                .map { it.index }
        val requested = indices.isNotEmpty()
        val flags = pkg.requestedPermissionsFlags
        val uncertain = requested && flags == null
        val granted = requested && flags != null && indices.any { i -> flags.getOrNull(i)?.let { it and FLAG_GRANTED != 0 } == true }
        val unexpected = granted && !pkg.isSystem && !expectations.isExpected(group, pkg.category, pkg.packageName)
        return PermissionRecord(
            packageName = pkg.packageName,
            group = group,
            requested = requested,
            granted = granted,
            uncertain = uncertain,
            unexpected = unexpected,
            api = API,
            collectedAt = collectedAt,
        )
    }

    companion object {
        const val API = "PackageInfo.requestedPermissionsFlags"

        /** PackageInfo.REQUESTED_PERMISSION_GRANTED */
        const val FLAG_GRANTED = 2

        val GROUP_PERMISSIONS: Map<SensitiveGroup, Set<String>> =
            mapOf(
                SensitiveGroup.LOCATION to setOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"),
                SensitiveGroup.LOCATION_BACKGROUND to setOf("android.permission.ACCESS_BACKGROUND_LOCATION"),
                SensitiveGroup.MICROPHONE to setOf("android.permission.RECORD_AUDIO"),
                SensitiveGroup.CAMERA to setOf("android.permission.CAMERA"),
            )
    }
}
