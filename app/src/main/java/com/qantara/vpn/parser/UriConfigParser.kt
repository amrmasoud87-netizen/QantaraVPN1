package com.qantara.vpn.parser

import android.net.Uri
import android.util.Base64
import com.qantara.vpn.model.ProtocolType
import com.qantara.vpn.model.SecurityType
import com.qantara.vpn.model.ServerNode
import com.qantara.vpn.model.TransportType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URLDecoder
import java.util.UUID

object UriConfigParser {

    private val prettyJson = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun parseInput(raw: String): Result<ServerNode> {
        val trimmed = raw.trim()
        return runCatching {
            when {
                trimmed.startsWith("vless://", ignoreCase = true) -> parseVless(trimmed)
                trimmed.startsWith("vmess://", ignoreCase = true) -> parseVmess(trimmed)
                trimmed.startsWith("trojan://", ignoreCase = true) -> parseTrojan(trimmed)
                trimmed.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(trimmed)
                trimmed.startsWith("{") -> parseXrayJson(trimmed)
                else -> throw IllegalArgumentException("Unsupported configuration format")
            }
        }
    }

    private fun parseVless(uriString: String): ServerNode {
        val uri = Uri.parse(uriString)
        val uuid = uri.userInfo ?: error("Missing VLESS UUID")
        val host = uri.host ?: error("Missing VLESS host")
        val port = if (uri.port > 0) uri.port else 443
        val label = decodeFragment(uri.fragment, "$host:$port")

        val type = mapTransport(uri.getQueryParameter("type"))
        val security = mapSecurity(uri.getQueryParameter("security"))

        return ServerNode(
            id = UUID.randomUUID().toString(),
            name = label,
            nameAr = label,
            countryCode = "CUSTOM",
            countryNameEn = "Custom Node",
            countryNameAr = "خادم مخصص",
            flagEmoji = "🌐",
            cityEn = host,
            cityAr = host,
            address = host,
            port = port,
            protocol = ProtocolType.VLESS,
            transport = type,
            security = security,
            uuidOrPassword = uuid,
            flow = uri.getQueryParameter("flow").orEmpty(),
            encryption = uri.getQueryParameter("encryption") ?: "none",
            sni = uri.getQueryParameter("sni") ?: uri.getQueryParameter("peer").orEmpty(),
            host = uri.getQueryParameter("host").orEmpty(),
            path = uri.getQueryParameter("path").orEmpty(),
            grpcServiceName = uri.getQueryParameter("serviceName").orEmpty(),
            grpcMode = uri.getQueryParameter("mode") ?: "gun",
            realityPublicKey = uri.getQueryParameter("pbk").orEmpty(),
            realityShortId = uri.getQueryParameter("sid").orEmpty(),
            realitySpiderX = uri.getQueryParameter("spx") ?: "/",
            fingerprint = uri.getQueryParameter("fp") ?: "chrome",
            udpEnabled = true,
            isCustom = true
        )
    }

    private fun parseVmess(uriString: String): ServerNode {
        val encoded = uriString.removePrefix("vmess://").trim()
        val decoded = String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
        val obj = Json.parseToJsonElement(decoded).jsonObject

        val ps = obj["ps"]?.jsonPrimitive?.content ?: "VMess Node"
        val add = obj["add"]?.jsonPrimitive?.content ?: error("Missing VMess address")
        val port = obj["port"]?.jsonPrimitive?.content?.toIntOrNull() ?: 443
        val id = obj["id"]?.jsonPrimitive?.content ?: error("Missing VMess UUID")
        val net = mapTransport(obj["net"]?.jsonPrimitive?.content)
        val tls = mapSecurity(obj["tls"]?.jsonPrimitive?.content)

        return ServerNode(
            id = UUID.randomUUID().toString(),
            name = ps,
            nameAr = ps,
            countryCode = "CUSTOM",
            countryNameEn = "Custom Node",
            countryNameAr = "خادم مخصص",
            flagEmoji = "🌐",
            cityEn = add,
            cityAr = add,
            address = add,
            port = port,
            protocol = ProtocolType.VMESS,
            transport = net,
            security = tls,
            uuidOrPassword = id,
            encryption = obj["scy"]?.jsonPrimitive?.content ?: "auto",
            sni = obj["sni"]?.jsonPrimitive?.content.orEmpty(),
            host = obj["host"]?.jsonPrimitive?.content.orEmpty(),
            path = obj["path"]?.jsonPrimitive?.content.orEmpty(),
            grpcServiceName = if (net == TransportType.GRPC) obj["path"]?.jsonPrimitive?.content.orEmpty() else "",
            udpEnabled = true,
            isCustom = true
        )
    }

    private fun parseTrojan(uriString: String): ServerNode {
        val uri = Uri.parse(uriString)
        val password = uri.userInfo ?: error("Missing Trojan password")
        val host = uri.host ?: error("Missing Trojan host")
        val port = if (uri.port > 0) uri.port else 443
        val label = decodeFragment(uri.fragment, "$host:$port")
        val type = mapTransport(uri.getQueryParameter("type"))
        val security = mapSecurity(uri.getQueryParameter("security") ?: "tls")

        return ServerNode(
            id = UUID.randomUUID().toString(),
            name = label,
            nameAr = label,
            countryCode = "CUSTOM",
            countryNameEn = "Custom Node",
            countryNameAr = "خادم مخصص",
            flagEmoji = "🌐",
            cityEn = host,
            cityAr = host,
            address = host,
            port = port,
            protocol = ProtocolType.TROJAN,
            transport = type,
            security = security,
            uuidOrPassword = password,
            sni = uri.getQueryParameter("sni") ?: host,
            host = uri.getQueryParameter("host").orEmpty(),
            path = uri.getQueryParameter("path").orEmpty(),
            grpcServiceName = uri.getQueryParameter("serviceName").orEmpty(),
            fingerprint = uri.getQueryParameter("fp") ?: "chrome",
            udpEnabled = true,
            isCustom = true
        )
    }

    private fun parseShadowsocks(uriString: String): ServerNode {
        val withoutScheme = uriString.removePrefix("ss://")
        val parts = withoutScheme.split("#", limit = 2)
        val label = if (parts.size == 2) URLDecoder.decode(parts[1], "UTF-8") else "Shadowsocks Node"
        val main = parts[0]

        val (method, password, host, port) = if (main.contains("@")) {
            val atSplit = main.split("@", limit = 2)
            val userInfoDecoded = try {
                String(Base64.decode(atSplit[0], Base64.URL_SAFE or Base64.NO_PADDING), Charsets.UTF_8)
            } catch (_: Exception) {
                atSplit[0]
            }
            val creds = userInfoDecoded.split(":", limit = 2)
            val hostPort = atSplit[1].substringBefore("?").split(":", limit = 2)
            Quad(creds[0], creds.getOrElse(1) { "" }, hostPort[0], hostPort[1].toInt())
        } else {
            val decoded = String(Base64.decode(main, Base64.URL_SAFE or Base64.NO_PADDING), Charsets.UTF_8)
            val atSplit = decoded.split("@", limit = 2)
            val creds = atSplit[0].split(":", limit = 2)
            val hostPort = atSplit[1].split(":", limit = 2)
            Quad(creds[0], creds[1], hostPort[0], hostPort[1].toInt())
        }

        return ServerNode(
            id = UUID.randomUUID().toString(),
            name = label,
            nameAr = label,
            countryCode = "CUSTOM",
            countryNameEn = "Custom Node",
            countryNameAr = "خادم مخصص",
            flagEmoji = "🌐",
            cityEn = host,
            cityAr = host,
            address = host,
            port = port,
            protocol = ProtocolType.SHADOWSOCKS,
            transport = TransportType.TCP,
            security = SecurityType.NONE,
            uuidOrPassword = password,
            method = method,
            udpEnabled = true,
            isCustom = true
        )
    }

    private fun parseXrayJson(rawJson: String): ServerNode {
        val root = Json.parseToJsonElement(rawJson).jsonObject
        val outbounds = root["outbounds"]?.jsonArray ?: error("JSON missing 'outbounds' array")
        val primary = outbounds.firstOrNull()?.jsonObject ?: error("Empty 'outbounds' array")
        val protocolStr = primary["protocol"]?.jsonPrimitive?.content ?: "vless"
        val protocol = when (protocolStr.lowercase()) {
            "vless" -> ProtocolType.VLESS
            "vmess" -> ProtocolType.VMESS
            "trojan" -> ProtocolType.TROJAN
            "shadowsocks" -> ProtocolType.SHADOWSOCKS
            else -> ProtocolType.VLESS
        }

        val settings = primary["settings"]?.jsonObject
        val stream = primary["streamSettings"]?.jsonObject
        val transport = mapTransport(stream?.get("network")?.jsonPrimitive?.content)
        val security = mapSecurity(stream?.get("security")?.jsonPrimitive?.content)

        val (address, port, credential) = extractJsonEndpoint(protocol, settings)
        val tag = primary["tag"]?.jsonPrimitive?.content ?: "$address:$port"

        return ServerNode(
            id = UUID.randomUUID().toString(),
            name = tag,
            nameAr = tag,
            countryCode = "CUSTOM",
            countryNameEn = "Imported JSON",
            countryNameAr = "ملف JSON مستورد",
            flagEmoji = "🌐",
            cityEn = address,
            cityAr = address,
            address = address,
            port = port,
            protocol = protocol,
            transport = transport,
            security = security,
            uuidOrPassword = credential,
            rawJsonConfig = rawJson,
            udpEnabled = true,
            isCustom = true
        )
    }

    fun toXrayJsonConfig(node: ServerNode, socksPort: Int = 10808): String {
        if (!node.rawJsonConfig.isNullOrBlank()) return node.rawJsonConfig

        val outbound = buildJsonObject {
            put("tag", "proxy")
            put("protocol", node.protocol.name.lowercase())
            put("settings", buildOutboundSettings(node))
            put("streamSettings", buildStreamSettings(node))
            put("mux", buildJsonObject {
                put("enabled", false)
                put("concurrency", -1)
            })
        }

        val root = buildJsonObject {
            put("log", buildJsonObject { put("loglevel", "warning") })
            put("stats", buildJsonObject {})
            put("policy", buildJsonObject {
                put("levels", buildJsonObject {
                    put("0", buildJsonObject {
                        put("statsUserUplink", true)
                        put("statsUserDownlink", true)
                    })
                })
                put("system", buildJsonObject {
                    put("statsOutboundUplink", true)
                    put("statsOutboundDownlink", true)
                })
            })
            put("inbounds", buildJsonArray {
                add(buildJsonObject {
                    put("tag", "socks-in")
                    put("port", socksPort)
                    put("listen", "127.0.0.1")
                    put("protocol", "socks")
                    put("settings", buildJsonObject {
                        put("auth", "noauth")
                        put("udp", node.udpEnabled)
                        put("userLevel", 0)
                    })
                    put("sniffing", buildJsonObject {
                        put("enabled", true)
                        put("destOverride", JsonArray(listOf(JsonPrimitive("http"), JsonPrimitive("tls"), JsonPrimitive("quic"))))
                    })
                })
            })
            put("outbounds", buildJsonArray {
                add(outbound)
                add(buildJsonObject {
                    put("tag", "direct")
                    put("protocol", "freedom")
                })
                add(buildJsonObject {
                    put("tag", "block")
                    put("protocol", "blackhole")
                })
            })
            put("dns", buildJsonObject {
                put("servers", JsonArray(listOf(JsonPrimitive("1.1.1.1"), JsonPrimitive("8.8.8.8"))))
            })
        }

        return prettyJson.encodeToString(JsonObject.serializer(), root)
    }

    private fun buildOutboundSettings(node: ServerNode): JsonObject = when (node.protocol) {
        ProtocolType.VLESS -> buildJsonObject {
            put("vnext", buildJsonArray {
                add(buildJsonObject {
                    put("address", node.address)
                    put("port", node.port)
                    put("users", buildJsonArray {
                        add(buildJsonObject {
                            put("id", node.uuidOrPassword)
                            put("encryption", node.encryption.ifBlank { "none" })
                            if (node.flow.isNotBlank() && node.security == SecurityType.REALITY) {
                                put("flow", node.flow)
                            }
                            put("level", 0)
                        })
                    })
                })
            })
        }

        ProtocolType.VMESS -> buildJsonObject {
            put("vnext", buildJsonArray {
                add(buildJsonObject {
                    put("address", node.address)
                    put("port", node.port)
                    put("users", buildJsonArray {
                        add(buildJsonObject {
                            put("id", node.uuidOrPassword)
                            put("security", node.encryption.ifBlank { "auto" })
                            put("level", 0)
                        })
                    })
                })
            })
        }

        ProtocolType.TROJAN -> buildJsonObject {
            put("servers", buildJsonArray {
                add(buildJsonObject {
                    put("address", node.address)
                    put("port", node.port)
                    put("password", node.uuidOrPassword)
                    put("level", 0)
                })
            })
        }

        ProtocolType.SHADOWSOCKS -> buildJsonObject {
            put("servers", buildJsonArray {
                add(buildJsonObject {
                    put("address", node.address)
                    put("port", node.port)
                    put("method", node.method)
                    put("password", node.uuidOrPassword)
                    put("uot", node.udpEnabled)
                })
            })
        }
    }

    private fun buildStreamSettings(node: ServerNode): JsonObject = buildJsonObject {
        put("network", node.transport.networkValue)
        put("security", node.security.securityValue)

        when (node.security) {
            SecurityType.REALITY -> put("realitySettings", buildJsonObject {
                put("serverName", node.sni.ifBlank { node.address })
                put("fingerprint", node.fingerprint.ifBlank { "chrome" })
                put("publicKey", node.realityPublicKey)
                put("shortId", node.realityShortId)
                put("spiderX", node.realitySpiderX.ifBlank { "/" })
            })
            SecurityType.TLS -> put("tlsSettings", buildJsonObject {
                put("serverName", node.sni.ifBlank { node.address })
                put("fingerprint", node.fingerprint.ifBlank { "chrome" })
                put("allowInsecure", false)
            })
            SecurityType.NONE -> Unit
        }

        when (node.transport) {
            TransportType.WS -> put("wsSettings", buildJsonObject {
                put("path", node.path.ifBlank { "/" })
                if (node.host.isNotBlank()) {
                    put("headers", buildJsonObject { put("Host", node.host) })
                }
            })
            TransportType.GRPC -> put("grpcSettings", buildJsonObject {
                put("serviceName", node.grpcServiceName.ifBlank { "grpc" })
                put("multiMode", node.grpcMode == "multi")
            })
            TransportType.H2 -> put("httpSettings", buildJsonObject {
                put("path", node.path.ifBlank { "/" })
                if (node.host.isNotBlank()) {
                    put("host", JsonArray(listOf(JsonPrimitive(node.host))))
                }
            })
            TransportType.QUIC -> put("quicSettings", buildJsonObject {
                put("security", "none")
                put("header", buildJsonObject { put("type", "none") })
            })
            TransportType.TCP -> Unit
        }
    }

    private fun extractJsonEndpoint(protocol: ProtocolType, settings: JsonObject?): Triple<String, Int, String> {
        if (settings == null) return Triple("0.0.0.0", 443, "")
        return when (protocol) {
            ProtocolType.VLESS, ProtocolType.VMESS -> {
                val vnext = settings["vnext"]?.jsonArray?.firstOrNull()?.jsonObject
                val addr = vnext?.get("address")?.jsonPrimitive?.content ?: "0.0.0.0"
                val port = vnext?.get("port")?.jsonPrimitive?.content?.toIntOrNull() ?: 443
                val id = vnext?.get("users")?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("id")?.jsonPrimitive?.content.orEmpty()
                Triple(addr, port, id)
            }
            ProtocolType.TROJAN, ProtocolType.SHADOWSOCKS -> {
                val srv = settings["servers"]?.jsonArray?.firstOrNull()?.jsonObject
                val addr = srv?.get("address")?.jsonPrimitive?.content ?: "0.0.0.0"
                val port = srv?.get("port")?.jsonPrimitive?.content?.toIntOrNull() ?: 443
                val pw = srv?.get("password")?.jsonPrimitive?.content.orEmpty()
                Triple(addr, port, pw)
            }
        }
    }

    private fun mapTransport(value: String?): TransportType = when (value?.lowercase()) {
        "ws", "websocket" -> TransportType.WS
        "grpc" -> TransportType.GRPC
        "h2", "http" -> TransportType.H2
        "quic", "udp" -> TransportType.QUIC
        else -> TransportType.TCP
    }

    private fun mapSecurity(value: String?): SecurityType = when (value?.lowercase()) {
        "reality" -> SecurityType.REALITY
        "tls", "xtls" -> SecurityType.TLS
        else -> SecurityType.NONE
    }

    private fun decodeFragment(fragment: String?, fallback: String): String {
        if (fragment.isNullOrBlank()) return fallback
        return runCatching { URLDecoder.decode(fragment, "UTF-8") }.getOrDefault(fallback)
    }

    private data class Quad(val a: String, val b: String, val c: String, val d: Int)
}
