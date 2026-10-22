package com.cyberrick.phoneinspector.rules

import com.cyberrick.phoneinspector.collect.TrackerCatalog
import com.cyberrick.phoneinspector.core.model.AppRecord
import com.cyberrick.phoneinspector.core.model.CheckKind
import com.cyberrick.phoneinspector.core.model.CheckResult
import com.cyberrick.phoneinspector.core.model.CheckStatus
import com.cyberrick.phoneinspector.core.model.ControlKind
import com.cyberrick.phoneinspector.core.model.ControlRecord
import com.cyberrick.phoneinspector.core.model.DeviceInfo
import com.cyberrick.phoneinspector.core.model.Evidence
import com.cyberrick.phoneinspector.core.model.InstallSource
import com.cyberrick.phoneinspector.core.model.PermissionRecord
import com.cyberrick.phoneinspector.core.model.PersistenceRecord
import com.cyberrick.phoneinspector.core.model.SensitiveGroup
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class RuleEngineTest {
    private val now = 1_792_411_200_000L
    private val rulesAsset = File(System.getProperty("user.dir") ?: ".", "src/main/assets/${RuleSet.ASSET}")
    private val rules = RuleSet.parse(rulesAsset.readText(Charsets.UTF_8))
    private val iocs =
        IocSet(
            packages = setOf("com.example.ioc.match"),
            domains = setOf("c2.listed.example"),
            certs = setOf("sha256:deadbeef"),
            packageSources = mapOf("com.example.ioc.match" to listOf("Test list A", "Test list B")),
        )
    private val engine = RuleEngine(rules, IocMatcher(iocs), KnownServices(setOf("spotify.com", "whatsapp.net")))

    private fun app(
        pkg: String,
        source: InstallSource = InstallSource.PLAY_STORE,
        system: Boolean = false,
        hidden: Boolean = false,
        disguised: Boolean = false,
        category: String? = null,
        certs: List<String> = emptyList(),
    ) = AppRecord(
        packageName = pkg,
        label = pkg,
        firstInstallAt = now - 86_400_000L,
        lastUpdateAt = now - 86_400_000L,
        installSource = source,
        signingCertSha256 = certs,
        isSystem = system,
        hasLauncherIcon = !hidden,
        hidden = hidden,
        disguised = disguised,
        category = category,
        api = "t",
        collectedAt = now,
    )

    private fun control(
        pkg: String,
        kind: ControlKind,
    ) = ControlRecord(kind, pkg, null, now - 86_400_000L, "t", now)

    private fun perm(
        pkg: String,
        group: SensitiveGroup,
        granted: Boolean = true,
        unexpected: Boolean = false,
    ) = PermissionRecord(pkg, group, requested = true, granted = granted, unexpected = unexpected, api = "t", collectedAt = now)

    private fun persist(
        pkg: String,
        boot: Boolean,
        heavy: Boolean,
    ) = PersistenceRecord(
        pkg,
        bootReceiver = boot,
        foregroundServiceMinutes7d = if (heavy) 9000 else 10,
        backgroundHeavy = heavy,
        api = "t",
        collectedAt = now,
    )

    private fun evidence(
        apps: List<AppRecord>,
        controls: List<ControlRecord> = emptyList(),
        permissions: List<PermissionRecord> = emptyList(),
        persistence: List<PersistenceRecord> = emptyList(),
    ) = Evidence(
        device = DeviceInfo("t", "t", "16", 36),
        collectedAt = now,
        checks = CheckKind.entries.map { CheckResult(it, CheckStatus.CHECKED) },
        apps = apps,
        controls = controls,
        permissions = permissions,
        persistence = persistence,
    )

    @Test
    fun `every finding carries rule id severity bucket and an evidence reference`() {
        val e =
            evidence(
                listOf(app("com.sysconfig.service", InstallSource.SIDELOADED, hidden = true)),
                controls = listOf(control("com.sysconfig.service", ControlKind.ACCESSIBILITY)),
            )

        val findings = engine.evaluate(e)

        assertThat(findings).isNotEmpty()
        findings.forEach {
            assertThat(it.ruleId).isNotEmpty()
            assertThat(it.packageName).isEqualTo("com.sysconfig.service")
            assertThat(it.evidenceRef).isNotEmpty()
            assertThat(it.reason).isNotEmpty()
        }
    }

    @Test
    fun `ioc package match is high and needs a professional`() {
        val findings = engine.evaluate(evidence(listOf(app("com.example.ioc.match", InstallSource.UNKNOWN))))

        val ioc = findings.single { it.ruleId == "ioc_match" }
        assertThat(ioc.severity).isEqualTo(Severity.HIGH)
        assertThat(ioc.bucket).isEqualTo(Bucket.NEEDS_PROFESSIONAL)
    }

    @Test
    fun `ioc match on a visible store install with an icon is medium worth reviewing not high`() {
        val visible = app("com.example.ioc.match", InstallSource.PLAY_STORE, hidden = false)

        val findings = engine.evaluate(evidence(listOf(visible)))

        assertThat(findings.filter { it.ruleId == "ioc_match" }).isEmpty()
        val f = findings.single { it.ruleId == "ioc_match_visible_store" }
        assertThat(f.severity).isEqualTo(Severity.MEDIUM)
        assertThat(f.bucket).isEqualTo(Bucket.WORTH_REMOVING)
        assertThat(f.reason).contains("listed by: Test list A, Test list B")
    }

    @Test
    fun `contact with a listed domain is medium for a visible store app and high otherwise`() {
        val visible = evidence(listOf(app("com.family.app"))).copy(flows = listOf(dns("com.family.app", "api.c2.listed.example", now)))
        val hidden =
            evidence(
                listOf(app("com.x", InstallSource.SIDELOADED, hidden = true)),
            ).copy(flows = listOf(dns("com.x", "api.c2.listed.example", now)))

        val v = engine.evaluate(visible)
        assertThat(v.filter { it.ruleId == "ioc_domain_contact" }).isEmpty()
        assertThat(v.single { it.ruleId == "ioc_domain_contact_visible_store" }.severity).isEqualTo(Severity.MEDIUM)
        assertThat(engine.evaluate(hidden).single { it.ruleId == "ioc_domain_contact" }.severity).isEqualTo(Severity.HIGH)
    }

    @Test
    fun `ioc match on a hidden store install stays high`() {
        val hidden = app("com.example.ioc.match", InstallSource.PLAY_STORE, hidden = true)

        val findings = engine.evaluate(evidence(listOf(hidden)))

        assertThat(findings.single { it.ruleId == "ioc_match" }.severity).isEqualTo(Severity.HIGH)
    }

    @Test
    fun `foreground location or camera on a store install is not an unexpected grant finding`() {
        val shop =
            evidence(
                listOf(app("com.shop")),
                permissions =
                    listOf(
                        perm("com.shop", SensitiveGroup.LOCATION, unexpected = true),
                        perm("com.shop", SensitiveGroup.CAMERA, unexpected = true),
                    ),
            )
        val mic = evidence(listOf(app("com.calc")), permissions = listOf(perm("com.calc", SensitiveGroup.MICROPHONE, unexpected = true)))
        val bg =
            evidence(listOf(app("com.bg")), permissions = listOf(perm("com.bg", SensitiveGroup.LOCATION_BACKGROUND, unexpected = true)))

        assertThat(engine.evaluate(shop)).isEmpty()
        assertThat(engine.evaluate(mic).single().ruleId).isEqualTo("unexpected_grant_store")
        assertThat(engine.evaluate(bg).single().ruleId).isEqualTo("unexpected_grant_store")
    }

    @Test
    fun `every finding carries a short summary distinct from the reason`() {
        val e =
            evidence(
                listOf(app("com.x", InstallSource.SIDELOADED, hidden = true)),
                controls = listOf(control("com.x", ControlKind.ACCESSIBILITY)),
            )

        engine.evaluate(e).forEach {
            assertThat(it.summary).isNotEmpty()
            assertThat(it.summary.length).isLessThan(it.reason.length)
        }
    }

    @Test
    fun `ioc match reason names the lists that carry the package`() {
        val findings = engine.evaluate(evidence(listOf(app("com.example.ioc.match", InstallSource.UNKNOWN))))

        assertThat(findings.single { it.ruleId == "ioc_match" }.reason).contains("listed by: Test list A, Test list B")
    }

    @Test
    fun `ioc cert only match says which lists when known or signing certificate otherwise`() {
        val findings = engine.evaluate(evidence(listOf(app("com.innocent.name", certs = listOf("sha256:deadbeef")))))

        assertThat(findings.single { it.ruleId.startsWith("ioc_match") }.reason).contains("listed by: signing certificate match")
    }

    @Test
    fun `ioc signing cert match is medium on a visible play install and high when hidden`() {
        val visible = engine.evaluate(evidence(listOf(app("com.innocent.name", certs = listOf("sha256:deadbeef")))))
        val hidden = engine.evaluate(evidence(listOf(app("com.innocent.name", certs = listOf("sha256:deadbeef"), hidden = true))))

        assertThat(visible.single { it.ruleId == "ioc_match_visible_store" }.severity).isEqualTo(Severity.MEDIUM)
        assertThat(hidden.single { it.ruleId == "ioc_match" }.severity).isEqualTo(Severity.HIGH)
    }

    @Test
    fun `hidden sideloaded app with accessibility is high`() {
        val e =
            evidence(
                listOf(app("com.x", InstallSource.SIDELOADED, hidden = true)),
                controls = listOf(control("com.x", ControlKind.ACCESSIBILITY)),
            )

        assertThat(engine.evaluate(e).map { it.ruleId to it.severity }).contains("hidden_nonstore_with_power" to Severity.HIGH)
    }

    @Test
    fun `hidden play installed app with mic alone is not high`() {
        val e =
            evidence(
                listOf(app("com.google.speech", hidden = true)),
                permissions = listOf(perm("com.google.speech", SensitiveGroup.MICROPHONE)),
            )

        assertThat(engine.evaluate(e).none { it.severity == Severity.HIGH }).isTrue()
    }

    @Test
    fun `hidden play installed app with device admin is medium`() {
        val e = evidence(listOf(app("com.x", hidden = true)), controls = listOf(control("com.x", ControlKind.DEVICE_ADMIN)))

        assertThat(engine.evaluate(e).map { it.ruleId to it.severity }).contains("hidden_store_with_control" to Severity.MEDIUM)
    }

    @Test
    fun `accessibility on a non system app outside the accessibility category is high`() {
        val e = evidence(listOf(app("com.x", category = "PRODUCTIVITY")), controls = listOf(control("com.x", ControlKind.ACCESSIBILITY)))

        assertThat(engine.evaluate(e).single { it.ruleId == "accessibility_non_system" }.severity).isEqualTo(Severity.HIGH)
    }

    @Test
    fun `accessibility on a system app or an accessibility category app is not flagged`() {
        val e =
            evidence(
                listOf(app("com.sys.talkback", system = true), app("com.store.screenreader", category = "ACCESSIBILITY")),
                controls =
                    listOf(
                        control("com.sys.talkback", ControlKind.ACCESSIBILITY),
                        control("com.store.screenreader", ControlKind.ACCESSIBILITY),
                    ),
            )

        assertThat(engine.evaluate(e).filter { it.ruleId == "accessibility_non_system" }).isEmpty()
    }

    @Test
    fun `device admin without a work profile is medium and with a profile owner is a low note`() {
        val antitheft = evidence(listOf(app("com.antitheft")), controls = listOf(control("com.antitheft", ControlKind.DEVICE_ADMIN)))
        val work =
            evidence(
                listOf(app("com.dpc")),
                controls = listOf(control("com.dpc", ControlKind.DEVICE_ADMIN), control("com.dpc", ControlKind.PROFILE_OWNER)),
            )

        assertThat(engine.evaluate(antitheft).single { it.ruleId == "device_admin_no_profile" }.severity).isEqualTo(Severity.MEDIUM)
        val workFindings = engine.evaluate(work)
        assertThat(workFindings.filter { it.ruleId == "device_admin_no_profile" }).isEmpty()
        assertThat(workFindings.single { it.ruleId == "work_profile_present" }.severity).isEqualTo(Severity.LOW)
    }

    @Test
    fun `disguised is medium`() {
        assertThat(
            engine
                .evaluate(evidence(listOf(app("com.update.core", InstallSource.SIDELOADED, disguised = true))))
                .single {
                    it.ruleId ==
                        "disguised_system_name"
                }.severity,
        ).isEqualTo(Severity.MEDIUM)
    }

    @Test
    fun `sideloaded with a sensitive grant is medium and sideloaded without is low`() {
        val withMic =
            evidence(listOf(app("com.a", InstallSource.SIDELOADED)), permissions = listOf(perm("com.a", SensitiveGroup.MICROPHONE)))
        val plain = evidence(listOf(app("com.b", InstallSource.SIDELOADED)))

        assertThat(engine.evaluate(withMic).single { it.ruleId == "sideloaded_with_sensitive" }.severity).isEqualTo(Severity.MEDIUM)
        assertThat(engine.evaluate(plain).single { it.ruleId == "sideloaded_no_sensitive" }.severity).isEqualTo(Severity.LOW)
    }

    @Test
    fun `unexpected grant is medium off store and low on store`() {
        val side =
            evidence(
                listOf(app("com.a", InstallSource.SIDELOADED)),
                permissions = listOf(perm("com.a", SensitiveGroup.CAMERA, unexpected = true)),
            )
        val store = evidence(listOf(app("com.b")), permissions = listOf(perm("com.b", SensitiveGroup.MICROPHONE, unexpected = true)))

        assertThat(engine.evaluate(side).single { it.ruleId == "unexpected_grant_nonstore" }.severity).isEqualTo(Severity.MEDIUM)
        assertThat(engine.evaluate(store).single { it.ruleId == "unexpected_grant_store" }.severity).isEqualTo(Severity.LOW)
    }

    @Test
    fun `maps app with expected background location yields nothing`() {
        val e =
            evidence(listOf(app("com.maps", category = "MAPS")), permissions = listOf(perm("com.maps", SensitiveGroup.LOCATION_BACKGROUND)))

        assertThat(engine.evaluate(e)).isEmpty()
    }

    @Test
    fun `boot receiver with heavy background is medium and heavy alone is low`() {
        val both = evidence(listOf(app("com.a")), persistence = listOf(persist("com.a", boot = true, heavy = true)))
        val heavyOnly = evidence(listOf(app("com.b")), persistence = listOf(persist("com.b", boot = false, heavy = true)))
        val bootOnly = evidence(listOf(app("com.c")), persistence = listOf(persist("com.c", boot = true, heavy = false)))

        assertThat(engine.evaluate(both).single { it.ruleId == "boot_and_background_heavy" }.severity).isEqualTo(Severity.MEDIUM)
        assertThat(engine.evaluate(heavyOnly).single { it.ruleId == "background_heavy_alone" }.severity).isEqualTo(Severity.LOW)
        assertThat(engine.evaluate(bootOnly)).isEmpty()
    }

    @Test
    fun `notification listener outside expected categories is medium off store and low on store`() {
        val side =
            evidence(
                listOf(app("com.a", InstallSource.SIDELOADED, category = "GAME")),
                controls = listOf(control("com.a", ControlKind.NOTIFICATION_LISTENER)),
            )
        val store =
            evidence(listOf(app("com.b", category = "GAME")), controls = listOf(control("com.b", ControlKind.NOTIFICATION_LISTENER)))
        val social =
            evidence(listOf(app("com.c", category = "SOCIAL")), controls = listOf(control("com.c", ControlKind.NOTIFICATION_LISTENER)))

        assertThat(engine.evaluate(side).single { it.ruleId == "notification_listener_nonstore" }.severity).isEqualTo(Severity.MEDIUM)
        assertThat(engine.evaluate(store).single { it.ruleId == "notification_listener_store" }.severity).isEqualTo(Severity.LOW)
        assertThat(engine.evaluate(social)).isEmpty()
    }

    @Test
    fun `a control grant on a package missing from the inventory is medium for a professional`() {
        val e = evidence(emptyList(), controls = listOf(control("com.ghost", ControlKind.DEVICE_ADMIN)))

        val f = engine.evaluate(e).single { it.ruleId == "control_on_unknown_package" }
        assertThat(f.severity).isEqualTo(Severity.MEDIUM)
        assertThat(f.bucket).isEqualTo(Bucket.NEEDS_PROFESSIONAL)
    }

    @Test
    fun `duplicate package entries are a low note`() {
        val e = evidence(listOf(app("com.dup").copy(duplicate = true), app("com.dup").copy(duplicate = true)))

        assertThat(engine.evaluate(e).filter { it.ruleId == "duplicate_package" }).hasSize(1)
    }

    @Test
    fun `findings are sorted by severity then package then rule`() {
        val e =
            evidence(
                listOf(app("com.z", InstallSource.SIDELOADED, hidden = true), app("com.a", InstallSource.SIDELOADED)),
                controls = listOf(control("com.z", ControlKind.ACCESSIBILITY)),
            )

        val findings = engine.evaluate(e)

        assertThat(findings.map { -it.severity.rank }).isInOrder()
        assertThat(findings.first().severity).isEqualTo(Severity.HIGH)
    }

    private fun dns(
        pkg: String,
        host: String,
        at: Long,
    ) = com.cyberrick.phoneinspector.core.model.FlowRecord(
        uid = 1,
        packageName = pkg,
        host = host,
        protocol = "udp",
        observedAt = at,
        api = "t",
        collectedAt = now,
    )

    private fun counter(
        pkg: String,
        bytes: Long,
    ) = com.cyberrick.phoneinspector.core.model.FlowRecord(
        uid = 1,
        packageName = pkg,
        host = null,
        protocol = "counter",
        observedAt = now,
        bytesSent = bytes,
        api = "t",
        collectedAt = now,
    )

    @Test
    fun `a sixty second beacon to an unrecognized host is medium and names the interval`() {
        val flows = (0 until 8).map { dns("com.photo", "c2.metrics-collector.xyz", now + it * 60_000L) }

        val f = engine.evaluate(evidence(listOf(app("com.photo"))).copy(flows = flows)).single { it.ruleId == "beacon_unrecognized_host" }

        assertThat(f.severity).isEqualTo(Severity.MEDIUM)
        assertThat(f.check).isEqualTo(CheckKind.NETWORK)
        assertThat(f.reason).contains("every 60 s")
        assertThat(f.reason).contains("c2.metrics-collector.xyz")
    }

    @Test
    fun `bytes sent with no lookups is medium raw ip suspect`() {
        val f = engine.evaluate(evidence(listOf(app("com.weather"))).copy(flows = listOf(counter("com.weather", 2_097_152)))).single()

        assertThat(f.ruleId).isEqualTo("raw_ip_suspect")
        assertThat(f.severity).isEqualTo(Severity.MEDIUM)
    }

    @Test
    fun `an unrecognized host alone is low and with another oddity on the app is medium`() {
        val alone = engine.evaluate(evidence(listOf(app("com.a"))).copy(flows = listOf(dns("com.a", "odd.example", now))))
        val withOddity =
            engine.evaluate(
                evidence(listOf(app("com.b", InstallSource.SIDELOADED))).copy(flows = listOf(dns("com.b", "odd.example", now))),
            )

        assertThat(alone.single().ruleId).isEqualTo("unrecognized_host_alone")
        assertThat(alone.single().severity).isEqualTo(Severity.LOW)
        assertThat(withOddity.single { it.ruleId == "unrecognized_host_with_oddity" }.severity).isEqualTo(Severity.MEDIUM)
    }

    @Test
    fun `known hosts only yield no network finding`() {
        val flows = (0 until 10).map { dns("com.spotify.music", "api.spotify.com", now + it * 60_000L) }

        assertThat(engine.evaluate(evidence(listOf(app("com.spotify.music"))).copy(flows = flows))).isEmpty()
    }

    @Test
    fun `network rule wording says unrecognized and never malicious`() {
        rules.rules.filter { it.check == CheckKind.NETWORK }.forEach {
            assertThat(it.reason.lowercase()).doesNotContain("malicious")
            assertThat(it.summary.lowercase()).doesNotContain("malicious")
        }
        assertThat(
            rules.rules
                .single { it.id == "unrecognized_host_alone" }
                .reason
                .lowercase(),
        ).contains("unrecognized")
    }

    @Test
    fun `every high and medium rule carries a label from the fixed set and a card sentence`() {
        rules.rules.filter { it.severity != Severity.LOW }.forEach {
            assertThat(it.label).isIn(Labels.ALL)
            assertThat(it.cardText).isNotEmpty()
            assertThat(it.cardText.length).isLessThan(140)
        }
        rules.rules.filter { it.severity == Severity.LOW }.forEach { assertThat(it.label).isEqualTo(Labels.NOTE) }
    }

    @Test
    fun `known malware hash is high with the family in the reason`() {
        val malware = MalwareHashes(mapOf("ab".repeat(32) to MalwareHashes.Entry("Cerberus", listOf("banker", "android"))))
        val engine =
            RuleEngine(
                rules,
                IocMatcher(iocs),
                KnownServices(emptySet()),
                intel = ThreatIntel(malware, MaliciousHosts(emptySet(), emptySet()), TrackerCatalog(emptyList())),
            )
        val e = evidence(listOf(app("com.innocent").copy(apkSha256 = "ab".repeat(32))))

        val f = engine.evaluate(e).single { it.ruleId == "known_malware_hash" }

        assertThat(f.severity).isEqualTo(Severity.HIGH)
        assertThat(f.reason).contains("Cerberus")
        assertThat(f.label).isEqualTo(Labels.KNOWN_MALWARE)
    }

    @Test
    fun `contact with a malicious host is high and adware heavy and tracker note follow the tracker counts`() {
        val catalog =
            TrackerCatalog(
                (1..10).map { TrackerCatalog.Tracker("Ad$it", listOf("Advertisement"), listOf("x$it")) } +
                    TrackerCatalog.Tracker("Prof", listOf("Profiling"), listOf("y")),
            )
        val hosts = MaliciousHosts(domains = setOf("drop.bad.example"), ips = emptySet())
        val engine =
            RuleEngine(rules, IocMatcher(iocs), KnownServices(emptySet()), intel = ThreatIntel(MalwareHashes(emptyMap()), hosts, catalog))
        val contact = evidence(listOf(app("com.a"))).copy(flows = listOf(dns("com.a", "cdn.drop.bad.example", now)))
        val adHeavy = evidence(listOf(app("com.b", InstallSource.SIDELOADED).copy(trackers = listOf("Ad1", "Ad2", "Ad3", "Ad4", "Ad5"))))
        val adHeavyStore = evidence(listOf(app("com.e").copy(trackers = (1..5).map { "Ad$it" } + "Prof")))
        val adHeavyStoreTen = evidence(listOf(app("com.f").copy(trackers = (1..10).map { "Ad$it" })))
        val profilingSideloaded = evidence(listOf(app("com.c", InstallSource.SIDELOADED).copy(trackers = listOf("Prof"))))
        val few = evidence(listOf(app("com.d").copy(trackers = listOf("Ad1"))))

        assertThat(engine.evaluate(contact).single { it.ruleId == "malicious_host_contact" }.severity).isEqualTo(Severity.HIGH)
        assertThat(engine.evaluate(adHeavy).single { it.ruleId == "adware_heavy" }.label).isEqualTo(Labels.ADWARE_HEAVY)
        assertThat(engine.evaluate(profilingSideloaded).map { it.ruleId }).contains("adware_heavy")
        assertThat(engine.evaluate(few).single().ruleId).isEqualTo("trackers_note")
        // From a store, ad heavy is a note, never a threat: the phone run showed 33 store games at five or more.
        assertThat(engine.evaluate(adHeavyStore).single().ruleId).isEqualTo("trackers_note")
        assertThat(engine.evaluate(adHeavyStoreTen).single().let { it.ruleId to it.severity }).isEqualTo(
            "adware_heavy_store" to Severity.LOW,
        )
    }

    @Test
    fun `bundled rules parse and every rule has id severity bucket reason and remediation`() {
        assertThat(rules.rules.size).isAtLeast(12)
        rules.rules.forEach {
            assertThat(it.id).matches("[a-z_]+")
            assertThat(it.reason).isNotEmpty()
            assertThat(it.remediation).isNotEmpty()
        }
        assertThat(rules.rules.map { it.id }).containsNoDuplicates()
    }
}
