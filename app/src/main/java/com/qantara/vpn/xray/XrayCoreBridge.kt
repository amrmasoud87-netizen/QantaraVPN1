package com.qantara.vpn.xray

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Native Xray-core execution bridge supporting two real Android deployment modes:
 *
 * 1. GoMobile AAR (`app/libs/libv2ray.aar` from 2dust/AndroidLibXrayLite or XTLS/libXray):
 *    Runs Xray-core + Go tun2socks in-process bound directly to the VpnService TUN fd.
 *
 * 2. Native ELF binaries (`app/src/main/jniLibs/<abi>/libxray.so` + `libtun2socks.so`):
 *    Android extracts ELF files in jniLibs to `applicationInfo.nativeLibraryDir` with +x
 *    permissions, allowing `libxray.so run -c xray_active.json` to bind local SOCKS5/UDP.
 */
class XrayCoreBridge(
    private val context: Context,
    private val protectFd: (Int) -> Boolean
) {

    private var coreController: Any? = null
    private var xrayProcess: Process? = null
    private var tun2socksProcess: Process? = null
    private var isRunning = false

    fun hasNativeCore(): Boolean {
        val hasLibv2ray = runCatching { Class.forName("libv2ray.Libv2ray") }.isSuccess
        val hasLibxray = File(context.applicationInfo.nativeLibraryDir, "libxray.so").exists()
        return hasLibv2ray || hasLibxray
    }

    fun startCore(xrayJsonConfig: String, tunFd: Int): Boolean {
        val configFile = File(context.filesDir, "xray_active.json").apply {
            writeText(xrayJsonConfig)
        }

        // Mode 1: GoMobile AAR (`app/libs/libv2ray.aar`)
        try {
            val libClass = Class.forName("libv2ray.Libv2ray")
            val envInit = libClass.getMethod("initCoreEnv", String::class.java, String::class.java)
            envInit.invoke(null, context.filesDir.absolutePath, "")

            val handlerClass = Class.forName("libv2ray.CoreCallbackHandler")
            val controller = libClass.getMethod("newCoreController", handlerClass)
                .invoke(null, createCallbackProxy(handlerClass))

            val startLoop = controller.javaClass.getMethod(
                "startLoop",
                String::class.java,
                Int::class.javaPrimitiveType
            )
            startLoop.invoke(controller, configFile.readText(), tunFd)
            coreController = controller
            isRunning = true
            return true
        } catch (_: ClassNotFoundException) {
            // Fall through to Mode 2 (`jniLibs/<abi>/libxray.so`)
        } catch (e: Exception) {
            Log.e(TAG, "libv2ray failed to start Xray-core", e)
            return false
        }

        // Mode 2: Standalone `libxray.so` + `libtun2socks.so` in `jniLibs/<abi>/`
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val xrayBin = File(nativeDir, "libxray.so")
        val tun2socksBin = File(nativeDir, "libtun2socks.so")

        if (!xrayBin.exists() || !tun2socksBin.exists()) {
            Log.e(
                TAG,
                "Missing native Xray-core binary. Place libv2ray.aar in app/libs/ OR " +
                    "libxray.so + libtun2socks.so in app/src/main/jniLibs/<abi>/."
            )
            return false
        }

        return try {
            xrayProcess = ProcessBuilder(xrayBin.absolutePath, "run", "-c", configFile.absolutePath)
                .directory(context.filesDir)
                .redirectErrorStream(true)
                .start()

            tun2socksProcess = ProcessBuilder(
                tun2socksBin.absolutePath,
                "--netif-ipaddr", "10.0.0.2",
                "--netif-netmask", "255.255.255.252",
                "--socks-server-addr", "127.0.0.1:10808",
                "--tunmtu", "1500",
                "--tunfd", tunFd.toString(),
                "--enable-udprelay"
            )
                .directory(context.filesDir)
                .redirectErrorStream(true)
                .start()

            isRunning = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start native libxray.so / libtun2socks.so", e)
            stopCore()
            false
        }
    }

    fun stopCore() {
        try {
            coreController?.let { controller ->
                val stopLoop = controller.javaClass.getMethod("stopLoop")
                stopLoop.invoke(controller)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping libv2ray controller", e)
        } finally {
            coreController = null
        }

        runCatching { tun2socksProcess?.destroy() }
        runCatching { xrayProcess?.destroy() }
        tun2socksProcess = null
        xrayProcess = null
        isRunning = false
    }

    fun queryStats(tag: String, link: String): Long {
        val controller = coreController ?: return -1L
        return try {
            val method = controller.javaClass.getMethod("queryStats", String::class.java, String::class.java)
            (method.invoke(controller, tag, link) as? Long) ?: 0L
        } catch (_: Exception) {
            -1L
        }
    }

    private fun createCallbackProxy(handlerInterface: Class<*>): Any {
        return java.lang.reflect.Proxy.newProxyInstance(
            handlerInterface.classLoader,
            arrayOf(handlerInterface)
        ) { _, method, args ->
            when (method.name) {
                "protect" -> {
                    val fd = (args?.firstOrNull() as? Int) ?: -1
                    if (fd >= 0) protectFd(fd) else false
                }
                "startup", "shutdown", "onEmitStatus" -> 0L
                else -> 0L
            }
        }
    }

    companion object {
        private const val TAG = "XrayCoreBridge"
    }
}
