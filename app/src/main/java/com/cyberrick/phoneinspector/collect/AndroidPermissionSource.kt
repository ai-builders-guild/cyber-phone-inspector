package com.cyberrick.phoneinspector.collect

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/** Read only adapter for the Permissions collector. */
class AndroidPermissionSource(
    context: Context,
) : PermissionSource {
    private val pm: PackageManager = context.packageManager

    override fun packagePermissions(): List<RawPackagePermissions> =
        pm.getInstalledPackages(PackageManager.GET_PERMISSIONS).map { info ->
            val app = info.applicationInfo
            val flags = app?.flags ?: 0
            RawPackagePermissions(
                packageName = info.packageName,
                requestedPermissions = info.requestedPermissions?.toList().orEmpty(),
                requestedPermissionsFlags = info.requestedPermissionsFlags?.toList(),
                category = AppsCollector.categoryName(app?.category),
                isSystem = flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
            )
        }
}
