package xyz.regulad.regulib.wifi

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import org.apache.commons.net.util.SubnetUtils
import xyz.regulad.regulib.StateFlowMapView.Companion.asMutableMap
import xyz.regulad.regulib.asLoopingSequence
import xyz.regulad.regulib.blockUntilAvailable
import xyz.regulad.regulib.transformState
import xyz.regulad.regulib.wifi.SubnetScanner.Companion.ICMP_PING_ASSESSOR
import xyz.regulad.regulib.wifi.SubnetScanner.Companion.createHttpPortAssessor
import xyz.regulad.regulib.wifi.SubnetScanner.Companion.createTcpPortAssessor
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.time.toJavaDuration

/**
 * A class that can scans a subnet for devices.
 *
 * So long as the subnet is allowed to run, the scanner will continue to update the [reachableAddresses] property with the addresses of devices that are reachable.
 *
 * Example:
 *
 * ```
 * val scanner = SubnetScanner(SubnetUtils("192.168.0.1/16").info)
 * scanner.reachableAddresses.collect { addresses ->
 *    println("Found reachable addresses: $addresses")
 * }
 * ```
 *
 * @param subnet The subnet to scan.
 * @param assessor A function that determines if an address is reachable. By default, this uses ICMP pings. You may also choose to use a TCP port assessor or an HTTP assessor.
 * @param threads The number of threads to use for scanning. Defaults to 16.
 *
 * @see ICMP_PING_ASSESSOR
 * @see createTcpPortAssessor
 * @see createHttpPortAssessor
 */
@Suppress("unused")
class SubnetScanner(
    subnet: SubnetUtils.SubnetInfo,
    private val assessor: suspend (InetAddress) -> Boolean = ICMP_PING_ASSESSOR,
    threads: Int = 16
) {
    companion object {
        fun fromCidr(cidr: String, assessor: suspend (InetAddress) -> Boolean = ICMP_PING_ASSESSOR, threads: Int = 16) =
            SubnetScanner(SubnetUtils(cidr).info, assessor, threads)

        private const val TAG = "SubnetScanner"

        private val DEFAULT_TIMEOUT = 10_000L.toDuration(DurationUnit.MILLISECONDS)

        /**
         * Checks if an address is reachable by pinging it.
         */
        val ICMP_PING_ASSESSOR: suspend (InetAddress) -> Boolean = { address: InetAddress ->
            // ignore the thread starvation warning: this always runs in a background thread
            address.isReachable(DEFAULT_TIMEOUT.inWholeMilliseconds.toInt())
        }

        /**
         * Checks if an address is reachable by attempting to connect to a TCP port.
         */
        fun createTcpPortAssessor(port: UShort, timeout: Duration = DEFAULT_TIMEOUT): suspend (InetAddress) -> Boolean {
            return { address ->
                try {
                    Socket(address, port.toInt()).use { socket ->
                        socket.connect(InetSocketAddress(address, port.toInt()), timeout.inWholeMilliseconds.toInt())
                        true
                    }
                } catch (e: Exception) {
                    false
                }
            }
        }

        /**
         * Checks if an address is reachable by attempting to connect to an HTTP port.
         */
        fun createHttpPortAssessor(
            port: UShort = 80u,
            path: String = "/",
            timeout: Duration = DEFAULT_TIMEOUT
        ): suspend (InetAddress) -> Boolean {
            val client = OkHttpClient.Builder()
                .connectTimeout(timeout.toJavaDuration())
                .callTimeout(timeout.toJavaDuration())
                .readTimeout(timeout.toJavaDuration())
                .writeTimeout(timeout.toJavaDuration())
                .build()
            return { address ->
                try {
                    client.newCall(
                        okhttp3.Request.Builder()
                            .url("http://${address.hostAddress}:${port.toShort()}$path")
                            .build()
                    ).execute()

                    true
                } catch (e: Exception) {
                    false
                }
            }
        }
    }

    private val scanningScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val scanningSemaphore = Semaphore(threads)

    private val allAddresses = subnet.allAddresses.asIterable().map { InetAddress.getByName(it) }

    private val reachableMap = MutableStateFlow(allAddresses.associateWith { false })
    private val mutableReachableMap = reachableMap.asMutableMap()

    /**
     * A [StateFlow] that emits a [Set] of [InetAddress]es that are reachable.
     *
     * New devices will be added to the set as they are discovered, and devices that are no longer reachable will be removed.
     */
    val reachableAddresses =
        reachableMap.transformState(scanningScope) { map -> map.filterValues { value -> value }.keys }

    private suspend fun scanAddress(address: InetAddress) = scanningSemaphore.withPermit {
        val reachable = assessor(address)
        mutableReachableMap[address] = reachable
    }

    private suspend fun startScanning() {
        allAddresses.asLoopingSequence().forEach { address ->
            scanningSemaphore.blockUntilAvailable()
            scanningScope.launch {
                try {
                    scanAddress(address)
                } catch (e: Exception) {
                    Log.e(TAG, "Error scanning address $address", e)
                }
            }
        }
    }

    init {
        scanningScope.launch {
            startScanning()
        }
    }

    fun stopScanning() {
        scanningScope.cancel()
    }

    protected fun finalize() {
        if (scanningScope.isActive) {
            Log.w(TAG, "SubnetScanner was not stopped before being finalized; stopping now")
            stopScanning()
        }
    }
}
