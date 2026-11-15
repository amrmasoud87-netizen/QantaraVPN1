package com.qantara.vpn.data

import android.content.Context
import com.qantara.vpn.model.ProtocolType
import com.qantara.vpn.model.SecurityType
import com.qantara.vpn.model.ServerNode
import com.qantara.vpn.model.TransportType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

class ServerRepository(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val storageFile = File(context.filesDir, "servers_v1.json")
    private val prefs = context.getSharedPreferences("qantara_prefs", Context.MODE_PRIVATE)

    private val _servers = MutableStateFlow<List<ServerNode>>(emptyList())
    val servers: StateFlow<List<ServerNode>> = _servers.asStateFlow()

    private val _selectedServerId = MutableStateFlow<String?>(null)
    val selectedServerId: StateFlow<String?> = _selectedServerId.asStateFlow()

    init {
        val loaded = loadFromDisk()
        _servers.value = loaded.ifEmpty { defaultCountryNodes() }
        val savedId = prefs.getString("selected_server_id", null)
        _selectedServerId.value = savedId ?: _servers.value.firstOrNull()?.id
        persistToDisk()
    }

    fun getSelectedServer(): ServerNode? {
        val id = _selectedServerId.value
        return _servers.value.firstOrNull { it.id == id } ?: _servers.value.firstOrNull()
    }

    fun getServerById(id: String?): ServerNode? =
        _servers.value.firstOrNull { it.id == id }

    fun selectServer(id: String) {
        _selectedServerId.value = id
        prefs.edit().putString("selected_server_id", id).apply()
    }

    fun addServer(node: ServerNode) {
        _servers.value = listOf(node) + _servers.value
        selectServer(node.id)
        persistToDisk()
    }

    fun removeServer(id: String) {
        _servers.value = _servers.value.filterNot { it.id == id }
        if (_selectedServerId.value == id) {
            _selectedServerId.value = _servers.value.firstOrNull()?.id
        }
        persistToDisk()
    }

    suspend fun pingAllServers() = withContext(Dispatchers.IO) {
        val updated = _servers.value.map { node ->
            val rtt = measureTcpLatency(node.address, node.port)
            node.copy(pingMs = rtt)
        }
        _servers.value = updated
        persistToDisk()
    }

    private fun measureTcpLatency(host: String, port: Int): Int {
        return try {
            val start = System.nanoTime()
            Socket().use { it.connect(InetSocketAddress(host, port), 1500) }
            ((System.nanoTime() - start) / 1_000_000L).toInt()
        } catch (_: Exception) {
            -1
        }
    }

    private fun loadFromDisk(): List<ServerNode> {
        if (!storageFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<ServerNode>>(storageFile.readText())
        }.getOrDefault(emptyList())
    }

    private fun persistToDisk() {
        runCatching {
            storageFile.writeText(json.encodeToString(_servers.value))
        }
    }

    private fun defaultCountryNodes(): List<ServerNode> = listOf(
        ServerNode(
            id = "de-fra-01",
            name = "Germany · Frankfurt",
            nameAr = "ألمانيا · فرانكفورت",
            countryCode = "DE",
            countryNameEn = "Germany",
            countryNameAr = "ألمانيا",
            flagEmoji = "🇩🇪",
            cityEn = "Frankfurt",
            cityAr = "فرانكفورت",
            address = "de-fra.qantara.example.net",
            port = 443,
            protocol = ProtocolType.VLESS,
            transport = TransportType.TCP,
            security = SecurityType.REALITY,
            uuidOrPassword = "11111111-1111-4111-8111-1111111111de",
            sni = "www.cloudflare.com",
            realityPublicKey = "replace-with-server-public-key",
            realityShortId = "6ba85179e30d4fc2",
            pingMs = 34
        ),
        ServerNode(
            id = "nl-ams-01",
            name = "Netherlands · Amsterdam",
            nameAr = "هولندا · أمستردام",
            countryCode = "NL",
            countryNameEn = "Netherlands",
            countryNameAr = "هولندا",
            flagEmoji = "🇳🇱",
            cityEn = "Amsterdam",
            cityAr = "أمستردام",
            address = "nl-ams.qantara.example.net",
            port = 443,
            protocol = ProtocolType.VLESS,
            transport = TransportType.GRPC,
            security = SecurityType.REALITY,
            uuidOrPassword = "11111111-1111-4111-8111-11111111110e",
            sni = "aws.amazon.com",
            grpcServiceName = "qantara-grpc",
            pingMs = 38
        ),
        ServerNode(
            id = "fr-par-01",
            name = "France · Paris",
            nameAr = "فرنسا · باريس",
            countryCode = "FR",
            countryNameEn = "France",
            countryNameAr = "فرنسا",
            flagEmoji = "🇫🇷",
            cityEn = "Paris",
            cityAr = "باريس",
            address = "fr-par.qantara.example.net",
            port = 443,
            protocol = ProtocolType.TROJAN,
            transport = TransportType.WS,
            security = SecurityType.TLS,
            uuidOrPassword = "trojan-fr-secret-key",
            sni = "fr-par.qantara.example.net",
            path = "/ws-tunnel",
            pingMs = 42
        ),
        ServerNode(
            id = "gb-lon-01",
            name = "UK · London",
            nameAr = "المملكة المتحدة · لندن",
            countryCode = "GB",
            countryNameEn = "UK",
            countryNameAr = "المملكة المتحدة",
            flagEmoji = "🇬🇧",
            cityEn = "London",
            cityAr = "لندن",
            address = "uk-lon.qantara.example.net",
            port = 443,
            protocol = ProtocolType.VLESS,
            transport = TransportType.H2,
            security = SecurityType.TLS,
            uuidOrPassword = "11111111-1111-4111-8111-1111111111ab",
            sni = "uk-lon.qantara.example.net",
            path = "/h2-stream",
            pingMs = 45
        ),
        ServerNode(
            id = "us-nyc-01",
            name = "USA · New York",
            nameAr = "الولايات المتحدة · نيويورك",
            countryCode = "US",
            countryNameEn = "USA",
            countryNameAr = "الولايات المتحدة",
            flagEmoji = "🇺🇸",
            cityEn = "New York",
            cityAr = "نيويورك",
            address = "us-nyc.qantara.example.net",
            port = 443,
            protocol = ProtocolType.VLESS,
            transport = TransportType.TCP,
            security = SecurityType.REALITY,
            uuidOrPassword = "11111111-1111-4111-8111-1111111111us",
            sni = "www.microsoft.com",
            pingMs = 92
        ),
        ServerNode(
            id = "ca-tor-01",
            name = "Canada · Toronto",
            nameAr = "كندا · تورونتو",
            countryCode = "CA",
            countryNameEn = "Canada",
            countryNameAr = "كندا",
            flagEmoji = "🇨🇦",
            cityEn = "Toronto",
            cityAr = "تورونتو",
            address = "ca-tor.qantara.example.net",
            port = 443,
            protocol = ProtocolType.VMESS,
            transport = TransportType.WS,
            security = SecurityType.TLS,
            uuidOrPassword = "11111111-1111-4111-8111-1111111111ca",
            sni = "ca-tor.qantara.example.net",
            path = "/vmess-ws",
            pingMs = 98
        ),
        ServerNode(
            id = "tr-ist-01",
            name = "Turkey · Istanbul",
            nameAr = "تركيا · إسطنبول",
            countryCode = "TR",
            countryNameEn = "Turkey",
            countryNameAr = "تركيا",
            flagEmoji = "🇹🇷",
            cityEn = "Istanbul",
            cityAr = "إسطنبول",
            address = "tr-ist.qantara.example.net",
            port = 443,
            protocol = ProtocolType.VLESS,
            transport = TransportType.GRPC,
            security = SecurityType.REALITY,
            uuidOrPassword = "11111111-1111-4111-8111-11111111117a",
            sni = "www.apple.com",
            grpcServiceName = "tr-grpc",
            pingMs = 54
        ),
        ServerNode(
            id = "sg-sin-01",
            name = "Singapore · Marina",
            nameAr = "سنغافورة · سنغافورة",
            countryCode = "SG",
            countryNameEn = "Singapore",
            countryNameAr = "سنغافورة",
            flagEmoji = "🇸🇬",
            cityEn = "Singapore",
            cityAr = "سنغافورة",
            address = "sg-sin.qantara.example.net",
            port = 8388,
            protocol = ProtocolType.SHADOWSOCKS,
            transport = TransportType.QUIC,
            security = SecurityType.NONE,
            uuidOrPassword = "ss-2022-blake3-secret",
            method = "2022-blake3-aes-256-gcm",
            pingMs = 136
        ),
        ServerNode(
            id = "jp-tyo-01",
            name = "Japan · Tokyo",
            nameAr = "اليابان · طوكيو",
            countryCode = "JP",
            countryNameEn = "Japan",
            countryNameAr = "اليابان",
            flagEmoji = "🇯🇵",
            cityEn = "Tokyo",
            cityAr = "طوكيو",
            address = "jp-tyo.qantara.example.net",
            port = 443,
            protocol = ProtocolType.VLESS,
            transport = TransportType.TCP,
            security = SecurityType.REALITY,
            uuidOrPassword = "11111111-1111-4111-8111-111111111110",
            sni = "www.sony.com",
            pingMs = 158
        ),
        ServerNode(
            id = "ae-dxb-01",
            name = "UAE · Dubai",
            nameAr = "الإمارات · دبي",
            countryCode = "AE",
            countryNameEn = "UAE",
            countryNameAr = "الإمارات العربية المتحدة",
            flagEmoji = "🇦🇪",
            cityEn = "Dubai",
            cityAr = "دبي",
            address = "ae-dxb.qantara.example.net",
            port = 443,
            protocol = ProtocolType.VLESS,
            transport = TransportType.WS,
            security = SecurityType.TLS,
            uuidOrPassword = "11111111-1111-4111-8111-1111111111ae",
            sni = "ae-dxb.qantara.example.net",
            path = "/dxb-ws",
            pingMs = 28
        ),
        ServerNode(
            id = "sa-ruh-01",
            name = "Saudi Arabia · Riyadh",
            nameAr = "السعودية · الرياض",
            countryCode = "SA",
            countryNameEn = "Saudi Arabia",
            countryNameAr = "المملكة العربية السعودية",
            flagEmoji = "🇸🇦",
            cityEn = "Riyadh",
            cityAr = "الرياض",
            address = "sa-ruh.qantara.example.net",
            port = 443,
            protocol = ProtocolType.VLESS,
            transport = TransportType.GRPC,
            security = SecurityType.REALITY,
            uuidOrPassword = "11111111-1111-4111-8111-11111111115a",
            sni = "www.cloudflare.com",
            grpcServiceName = "ruh-grpc",
            pingMs = 24
        )
    )
}
