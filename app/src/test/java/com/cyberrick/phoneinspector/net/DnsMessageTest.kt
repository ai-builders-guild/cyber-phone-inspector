package com.cyberrick.phoneinspector.net

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class DnsMessageTest {
    @Test
    fun `parses a query id and question name and type`() {
        val m = DnsMessage.parse(TestBytes.DNS_QUERY_EXAMPLE_A)

        assertThat(m.id).isEqualTo(0x1234)
        assertThat(m.isResponse).isFalse()
        assertThat(m.question?.name).isEqualTo("example.com")
        assertThat(m.question?.type).isEqualTo(1)
    }

    @Test
    fun `parses a response with a compression pointer and an A answer`() {
        val m = DnsMessage.parse(TestBytes.DNS_RESPONSE_EXAMPLE_A)

        assertThat(m.isResponse).isTrue()
        assertThat(m.answerAddresses).containsExactly("93.184.216.34")
    }

    @Test
    fun `follows a cname and parses an AAAA answer`() {
        val m = DnsMessage.parse(TestBytes.DNS_RESPONSE_CNAME_AAAA)

        assertThat(m.question?.name).isEqualTo("www.example.com")
        assertThat(m.cnames).containsExactly("example.com")
        assertThat(m.answerAddresses).containsExactly("2606:2800:220:1:248:1893:25c8:1946")
    }

    @Test
    fun `a pointer loop is rejected`() {
        val err = assertThrows(DnsParseError::class.java) { DnsMessage.parse(TestBytes.DNS_LABEL_LOOP) }

        assertThat(err.message).contains("pointer")
    }

    @Test
    fun `a truncated message is rejected`() {
        assertThrows(DnsParseError::class.java) { DnsMessage.parse(TestBytes.DNS_TRUNCATED) }
    }

    @Test
    fun `an empty buffer is rejected`() {
        assertThrows(DnsParseError::class.java) { DnsMessage.parse(ByteArray(0)) }
    }

    @Test
    fun `the id can be rewritten without touching the rest`() {
        val rewritten = DnsMessage.withId(TestBytes.DNS_QUERY_EXAMPLE_A, 0xBEEF)

        assertThat(DnsMessage.parse(rewritten).id).isEqualTo(0xBEEF)
        assertThat(
            rewritten.copyOfRange(2, rewritten.size),
        ).isEqualTo(TestBytes.DNS_QUERY_EXAMPLE_A.copyOfRange(2, TestBytes.DNS_QUERY_EXAMPLE_A.size))
    }
}
