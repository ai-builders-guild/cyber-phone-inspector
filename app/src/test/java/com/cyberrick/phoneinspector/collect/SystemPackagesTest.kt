package com.cyberrick.phoneinspector.collect

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class SystemPackagesTest {
    private val asset = File(System.getProperty("user.dir") ?: ".", "src/main/assets/${SystemPackages.ASSET}")

    @Test
    fun `bundled system package list parses and is sorted`() {
        val packages = SystemPackages.parse(asset.readText(Charsets.UTF_8))

        assertThat(packages.size).isAtLeast(100)
        assertThat(packages).contains("com.android.systemui")
        assertThat(packages.toList()).isInOrder()
    }
}
