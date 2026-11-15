package com.qantara.vpn

import com.qantara.vpn.model.ProtocolType
import com.qantara.vpn.model.SecurityType
import com.qantara.vpn.model.TransportType
import com.qantara.vpn.parser.UriConfigParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UriConfigParserTest {

    @Test
    fun parsesVlessRealityGrpcLink() {
        val uri = "vless://11111111-1111-4111-8111-111111111111@de.example.com:443?security=reality&type=grpc&serviceName=qantara&sni=www.cloudflare.com&pbk=testPubKey&sid=6ba8#Germany-Reality"
        val result = UriConfigParser.parseInput(uri)
        assertTrue(result.isSuccess)
        val node = result.getOrThrow()
        assertEquals(ProtocolType.VLESS, node.protocol)
        assertEquals(TransportType.GRPC, node.transport)
        assertEquals(SecurityType.REALITY, node.security)
        assertEquals("de.example.com", node.address)
        assertEquals(443, node.port)
        assertEquals("qantara", node.grpcServiceName)
    }
}
