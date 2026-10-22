package com.cyberrick.phoneinspector

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry

/** Shell access the instrumentation has on an emulator: grants the test device's own consents, never a user's. */
object TestShell {
    val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    fun run(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { fd ->
            java.io.FileInputStream(fd.fileDescriptor).readBytes()
        }
    }

    fun grantUsageAccess(allow: Boolean) = run("appops set ${context.packageName} GET_USAGE_STATS ${if (allow) "allow" else "deny"}")

    fun resetConsentFlag() {
        context
            .getSharedPreferences("cpi", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
