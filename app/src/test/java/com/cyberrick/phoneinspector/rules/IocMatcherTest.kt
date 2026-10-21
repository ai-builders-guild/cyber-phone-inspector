package com.cyberrick.phoneinspector.rules

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class IocMatcherTest {
    private val assetDir = File(System.getProperty("user.dir") ?: ".", "src/main/assets/iocs")

    @Test
    fun `bundled snapshot loads offline with a date and non empty package and domain sets`() {
        val snapshot = IocSet.parse(File(assetDir, IocSet.ASSET).readText(Charsets.UTF_8))

        assertThat(snapshot.generatedAt).matches("\\d{4}-\\d{2}-\\d{2}")
        assertThat(snapshot.packages.size).isAtLeast(50)
        assertThat(snapshot.domains.size).isAtLeast(500)
        assertThat(snapshot.sources.size).isAtLeast(10)
    }

    @Test
    fun `the shipped snapshot never contains the test only package id`() {
        val snapshot = IocSet.parse(File(assetDir, IocSet.ASSET).readText(Charsets.UTF_8))

        assertThat(snapshot.packages).doesNotContain("com.example.ioc.match")
    }

    @Test
    fun `matches package ids case sensitively and certs case insensitively`() {
        val m = IocMatcher(IocSet(packages = setOf("com.bad.app"), domains = setOf("c2.evil.example"), certs = setOf("sha256:ABCDEF")))

        assertThat(m.packageHit("com.bad.app")).isTrue()
        assertThat(m.packageHit("com.Bad.app")).isFalse()
        assertThat(m.certHit(listOf("sha256:abcdef"))).isTrue()
        assertThat(m.certHit(listOf("sha256:000000"))).isFalse()
    }

    @Test
    fun `domain hit matches the host and any parent domain`() {
        val m = IocMatcher(IocSet(packages = emptySet(), domains = setOf("evil.example"), certs = emptySet()))

        assertThat(m.domainHit("c2.evil.example")).isTrue()
        assertThat(m.domainHit("evil.example")).isTrue()
        assertThat(m.domainHit("notevil.example")).isFalse()
        assertThat(m.domainHit("evil.example.com")).isFalse()
    }

    @Test
    fun `sources for a matched package name the lists that carry it`() {
        val m =
            IocMatcher(
                IocSet(
                    packages = setOf("com.bad.app"),
                    domains = emptySet(),
                    certs = emptySet(),
                    packageSources =
                        mapOf(
                            "com.bad.app" to listOf("Echap stalkerware", "Other list"),
                        ),
                ),
            )

        assertThat(m.sourcesFor("com.bad.app")).containsExactly("Echap stalkerware", "Other list").inOrder()
        assertThat(m.sourcesFor("com.unknown")).isEmpty()
    }
}
