package com.cyberrick.phoneinspector.collect

/** Requested permissions and their flags for one package, plus what the need heuristic may consider. */
data class RawPackagePermissions(
    val packageName: String,
    val requestedPermissions: List<String>,
    /** Parallel to [requestedPermissions]; null on devices that do not populate it (treated as not granted, uncertain). */
    val requestedPermissionsFlags: List<Int>?,
    val category: String?,
    val isSystem: Boolean,
)

interface PermissionSource {
    fun packagePermissions(): List<RawPackagePermissions>
}
