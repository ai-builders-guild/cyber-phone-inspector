package com.cyberrick.phoneinspector.feeds

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FeedRefresherTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val moduleDir = File(System.getProperty("user.dir") ?: ".")
    private val assets = File(moduleDir, "src/main/assets/feeds")

    private fun bundled(f: FeedFile): String = File(assets, f.fileName).readText(Charsets.UTF_8)

    /** A fake repo: answers per file name and records every address asked for. */
    private class Repo(
        private val answers: Map<String, ByteArray>,
    ) : Fetcher {
        val asked = mutableListOf<String>()

        override fun get(url: String): ByteArray {
            asked += url
            return answers[url.substringAfterLast('/')] ?: throw IllegalStateException("HTTP 404")
        }
    }

    private fun newer(f: FeedFile): String = bundled(f).replaceFirst("\"generatedAt\": \"2026-10-22\"", "\"generatedAt\": \"2026-10-29\"")

    @Test
    fun `valid newer lists replace the files atomically and dates follow`() {
        val repo = Repo(FeedFile.entries.associate { it.fileName to newer(it).toByteArray() })
        val dir = tmp.newFolder("feeds")

        val result = FeedRefresher(dir, repo, bundled = ::bundled).refresh()

        assertThat(result.ok).isTrue()
        assertThat(result.updated).containsExactlyElementsIn(FeedFile.entries)
        assertThat(FeedFile.dateOf(File(dir, "spyware.json").readText())).isEqualTo("2026-10-29")
        assertThat(dir.listFiles()!!.map { it.name }.filter { it.endsWith(".tmp") }).isEmpty()
        assertThat(repo.asked).containsExactlyElementsIn(FeedFile.entries.map { FeedRefresher.BASE + it.fileName })
    }

    @Test
    fun `a truncated or empty download is refused and the previous file kept`() {
        val dir = tmp.newFolder("feeds")
        File(dir, "trackers.json").writeText(bundled(FeedFile.TRACKERS))
        val truncated = bundled(FeedFile.TRACKERS).take(300)
        val repo = Repo(mapOf("trackers.json" to truncated.toByteArray(), "spyware.json" to ByteArray(0)))

        val result = FeedRefresher(dir, repo, bundled = ::bundled).refresh()

        assertThat(
            result.rejected.keys,
        ).containsExactly(FeedFile.TRACKERS, FeedFile.SPYWARE, FeedFile.MALWARE_HASHES, FeedFile.MALICIOUS_HOSTS)
        assertThat(result.rejected[FeedFile.TRACKERS]).startsWith("not a valid list")
        assertThat(File(dir, "trackers.json").readText()).isEqualTo(bundled(FeedFile.TRACKERS))
        assertThat(File(dir, "spyware.json").exists()).isFalse()
    }

    @Test
    fun `a list that parses but shrank by more than half is refused`() {
        val dir = tmp.newFolder("feeds")
        File(dir, "trackers.json").writeText(bundled(FeedFile.TRACKERS))
        val tiny =
            """{"generatedAt": "2026-10-29", "trackers": [{"name": "X", "categories": ["Advertisement"], "signatures": ["com.x."]}]}"""
        val repo = Repo(mapOf("trackers.json" to tiny.toByteArray()))

        val result = FeedRefresher(dir, repo, bundled = ::bundled).refresh()

        assertThat(result.rejected[FeedFile.TRACKERS]).contains("much smaller")
        assertThat(File(dir, "trackers.json").readText()).isEqualTo(bundled(FeedFile.TRACKERS))
    }

    @Test
    fun `an unchanged list is reported unchanged and not rewritten`() {
        val dir = tmp.newFolder("feeds")
        val repo = Repo(FeedFile.entries.associate { it.fileName to bundled(it).toByteArray() })

        val result = FeedRefresher(dir, repo, bundled = ::bundled).refresh()

        assertThat(result.unchanged).containsExactlyElementsIn(FeedFile.entries)
        assertThat(dir.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `the fetcher refuses anything that is not https on the feeds host before connecting`() {
        val fetcher = HttpsFetcher()

        assertThat(fetcher.allowed(FeedRefresher.BASE + "spyware.json")).isTrue()
        assertThat(fetcher.allowed("http://raw.githubusercontent.com/x")).isFalse()
        assertThat(fetcher.allowed("https://example.com/feeds/spyware.json")).isFalse()
        assertThat(fetcher.allowed("https://raw.githubusercontent.com.evil.example/x")).isFalse()
        val thrown = runCatching { fetcher.get("https://example.com/feeds/spyware.json") }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `the refresher only ever asks for the fixed base address`() {
        val repo = Repo(emptyMap())

        FeedRefresher(tmp.newFolder("feeds"), repo).refresh()

        assertThat(repo.asked.all { it.startsWith(FeedRefresher.BASE) }).isTrue()
    }
}
