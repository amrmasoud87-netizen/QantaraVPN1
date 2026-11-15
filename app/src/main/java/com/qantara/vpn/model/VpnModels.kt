package com.qantara.vpn.model

import kotlinx.serialization.Serializable

@Serializable
enum class ProtocolType(val displayName: String) {
    VLESS("VLESS"),
    VMESS("VMess"),
    TROJAN("Trojan"),
    SHADOWSOCKS("Shadowsocks")
}

@Serializable
enum class TransportType(val networkValue: String, val displayName: String) {
    TCP("tcp", "TCP"),
    WS("ws", "WebSocket"),
    GRPC("grpc", "gRPC"),
    H2("h2", "HTTP/2"),
    QUIC("quic", "UDP / QUIC")
}

@Serializable
enum class SecurityType(val securityValue: String, val displayName: String) {
    NONE("none", "None"),
    TLS("tls", "TLS"),
    REALITY("reality", "Reality")
}

@Serializable
data class ServerNode(
    val id: String,
    val name: String,
    val nameAr: String,
    val countryCode: String,
    val countryNameEn: String,
    val countryNameAr: String,
    val flagEmoji: String,
    val cityEn: String,
    val cityAr: String,
    val address: String,
    val port: Int,
    val protocol: ProtocolType,
    val transport: TransportType = TransportType.TCP,
    val security: SecurityType = SecurityType.REALITY,
    val uuidOrPassword: String,
    val flow: String = "xtls-rprx-vision",
    val encryption: String = "none",
    val method: String = "aes-256-gcm",
    val sni: String = "",
    val host: String = "",
    val path: String = "",
    val grpcServiceName: String = "",
    val grpcMode: String = "gun",
    val realityPublicKey: String = "",
    val realityShortId: String = "",
    val realitySpiderX: String = "/",
    val fingerprint: String = "chrome",
    val udpEnabled: Boolean = true,
    val rawJsonConfig: String? = null,
    val pingMs: Int = -1,
    val isCustom: Boolean = false
) {
    fun localizedCountry(isArabic: Boolean): String = if (isArabic) countryNameAr else countryNameEn
    fun localizedCity(isArabic: Boolean): String = if (isArabic) cityAr else cityEn
    fun localizedName(isArabic: Boolean): String = if (isArabic) nameAr else name
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}

data class TrafficStats(
    val pingMs: Int = 0,
    val uploadBytesPerSec: Long = 0L,
    val downloadBytesPerSec: Long = 0L,
    val packetLossPercent: Float = 0f,
    val totalUploadBytes: Long = 0L,
    val totalDownloadBytes: Long = 0L,
    val connectedSeconds: Long = 0L
)
