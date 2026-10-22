package com.cyberrick.phoneinspector.collect

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class ApkHashCollectorTest {
    private val bytesA = "hello apk".toByteArray()

    private fun source(vararg refs: Pair<ApkRef, ByteArray?>) =
        object : ApkSource {
            override fun nonSystemApks(): List<ApkRef> = refs.map { it.first }

            override fun open(ref: ApkRef): InputStream? = refs.first { it.first == ref }.second?.let { ByteArrayInputStream(it) }
        }

    @Test
    fun `hashes each readable base apk and reports null for an unreadable one`() {
        val a = ApkRef("com.a", "/data/app/a/base.apk", isSplit = false)
        val b = ApkRef("com.b", "/data/app/b/base.apk", isSplit = true)
        val c = ApkRef("com.c", "/data/app/c/base.apk", isSplit = false)

        val result = ApkHashCollector(source(a to bytesA, b to bytesA, c to null)).collect()

        assertThat(result.hashes["com.a"]).hasLength(64)
        assertThat(result.hashes["com.a"]).isEqualTo(result.hashes["com.b"])
        assertThat(result.hashes["com.c"]).isNull()
        assertThat(result.unreadable).isEqualTo(1)
        assertThat(result.splitPackages).containsExactly("com.b")
    }

    @Test
    fun `an empty source yields an empty result with nothing unreadable`() {
        val result = ApkHashCollector(source()).collect()

        assertThat(result.hashes).isEmpty()
        assertThat(result.unreadable).isEqualTo(0)
    }
}
