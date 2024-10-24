package xyz.regulad.regulib.wifi

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.MacAddress
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.net.wifi.p2p.*
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pServiceRequest
import android.os.Build
import android.os.Handler
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.commons.net.util.SubnetUtils
import xyz.regulad.regulib.waitForTrue
import java.util.*
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * A view that provides access to the [WifiP2pManager] for the given [Context].
 *
 * This class uses the [WifiP2pManager] to provide a higher-level API for managing Wi-Fi Direct using Kotlin Coroutines and provides a safe and stable way to access the [WifiP2pManager] API.
 *
 * Example (in an async context):
 */
@Suppress("unused")
class WifiP2pManagerView private constructor(private val wifiP2pManager: WifiP2pManager, private val context: Context) {
    companion object {
        private const val TAG = "WifiP2pManagerView"
        private const val GROUP_INFO_CHECK_INTERVAL = 5000L

        const val WIFI_P2P_SUBNET =
            "192.168.49.0/24" // Android's default Wi-Fi P2P subnet, not sure if this is fixed but I've never observed it changing
        val WIFI_P2P_SUBNET_INFO = SubnetUtils(WIFI_P2P_SUBNET).info

        private val viewMap: MutableMap<Context, WifiP2pManagerView> = Collections.synchronizedMap(WeakHashMap())

        /**
         * Get the [WifiP2pManagerView] for the given [Context].
         *
         * This will return the same instance for the same [Context].
         */
        fun Context.getWifiP2pManagerView(): WifiP2pManagerView {
            return viewMap.getOrPut(this) {
                val wifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
                WifiP2pManagerView(wifiP2pManager, this).apply {
                    initialize()
                }
            }
        }
    }

    // flows & broadcast

    private val backgroundTaskCoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _p2pEnabledState = MutableStateFlow(true)

    /**
     * The current state of Wi-Fi P2P.
     */
    val p2pEnabledState = _p2pEnabledState.asStateFlow()

    private val _peersFlow = MutableStateFlow<Collection<WifiP2pDevice>>(emptyList())

    /**
     * The current list of peers.
     *
     * You must start peer discovery before this will be updated.
     * @see discoverPeers
     */
    val peersFlow = _peersFlow.asStateFlow()

    private fun updatePeers() {
        backgroundTaskCoroutineScope.launch {
            try {
                _peersFlow.value = requestPeers()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update peers", e)
            }
        }
    }

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)

    /**
     * The current connection info.
     */
    val connectionInfo = _connectionInfo.asStateFlow()

    private fun updateConnectionInfo() {
        backgroundTaskCoroutineScope.launch {
            try {
                _connectionInfo.value = requestConnectionInfo()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update connection info", e)
            }
        }
    }

    private val _thisDeviceInfo = MutableStateFlow<WifiP2pDevice?>(null)

    /**
     * This device's information.
     */
    val thisDeviceInfo = _thisDeviceInfo.asStateFlow()

    private val _discoveryState = MutableStateFlow(false)

    /**
     * The current discovery state.
     */
    val discoveryState = _discoveryState.asStateFlow()

    private val _thisGroupInfo = MutableStateFlow<WifiP2pGroup?>(null)

    /**
     * This group's information. Due to an Android bug, this may not be updated immediately after a group is created or devices leave/join. However, it will update every [GROUP_INFO_CHECK_INTERVAL] ms.
     */
    val thisGroupInfo = _thisGroupInfo.asStateFlow()

    /**
     * Schedules the group info to be updated in the background.
     */
    private fun updateGroupInfo() {
        backgroundTaskCoroutineScope.launch {
            try {
                _thisGroupInfo.value = requestGroupInfo()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update group info", e)
            }
        }
    }

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    // Check if Wi-Fi P2P is supported and enabled
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    _p2pEnabledState.value = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                }

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    // The peer list has changed
                    updatePeers()
                }

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    // Connection state changed
                    val networkInfo: NetworkInfo? = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        // We are connected with the other device, request connection
                        // info to find group owner IP
                        updateConnectionInfo()
                    }
                    updateGroupInfo()
                }

                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    // This device's details have changed
                    val device: WifiP2pDevice? = intent.getParcelableExtra(
                        WifiP2pManager.EXTRA_WIFI_P2P_DEVICE
                    )
                    // Update UI with device's details
                    _thisDeviceInfo.value = device
                }

                WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {
                    // Discovery state changed
                    val state = intent.getIntExtra(
                        WifiP2pManager.EXTRA_DISCOVERY_STATE,
                        WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED
                    )
                    if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) {
                        // Discovery started
                        _discoveryState.value = true
                    } else if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED) {
                        // Discovery stopped
                        _discoveryState.value = false
                    }
                }
            }
        }
    }

    // setupSteps

    private val mainLooper = context.mainLooper
    private val mainHandler = Handler(mainLooper)
    private val mainExecutor = let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.mainExecutor
        } else {
            Executor { command -> mainHandler.post(command) }
        }
    }
    private val managerInitializedState = MutableStateFlow(false)
    private lateinit var frameworkChannel: WifiP2pManager.Channel

    private fun initialize(firstAttempt: Boolean = true) {
        frameworkChannel = wifiP2pManager.initialize(context, mainLooper) {
            managerInitializedState.value = false
            if (firstAttempt) {
                Log.w(TAG, "WifiP2pManager disconnected from framework channel! Reinitializing...")
                initialize(false)
            } else {
                Log.e(TAG, "WifiP2pManager disconnected from framework channel! Unable to reinitialize.")
                teardown() // avoid leaving resources open
            }
        }
        managerInitializedState.value = true

        // we can allow background stuff to begin now
        context.registerReceiver(receiver, intentFilter)

        // setup the background tasks
        backgroundTaskCoroutineScope.launch {
            while (isActive) {
                updateGroupInfo()
                delay(GROUP_INFO_CHECK_INTERVAL)
            }
        }
    }

    /**
     * Tears down the [WifiP2pManager] and unregisters the [BroadcastReceiver].
     *
     * This can only be called once.
     */
    fun teardown() {
        if (this::frameworkChannel.isInitialized && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            frameworkChannel.close()
        }
        context.unregisterReceiver(receiver)
        managerInitializedState.value = false
    }

    protected fun finalize() {
        if (managerInitializedState.value) {
            Log.d(TAG, "WifiP2pManagerView was not torn down before being finalized. Tearing down now.")
            try {
                teardown()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to teardown WifiP2pManagerView", e)
            }
        }
    }

    // methods
    private val managerLock = Mutex()

    /**
     * @see WifiP2pManager.addLocalService
     */
    @RequiresPermission(
        allOf = [
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_FINE_LOCATION
        ], conditional = true
    )
    suspend fun addLocalService(servInfo: WifiP2pServiceInfo) = managerLock.withLock {
        managerInitializedState.waitForTrue()
        val operationResult = suspendCoroutine<Result<Unit>> { continuation ->
            wifiP2pManager.addLocalService(frameworkChannel, servInfo, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    continuation.resume(Result.success(Unit))
                }

                override fun onFailure(reason: Int) {
                    continuation.resume(Result.failure(Exception("Failed to add local service: $reason")))
                }
            })
        }
        return@withLock operationResult.getOrThrow()
    }

    /**
     * @see WifiP2pManager.addServiceRequest
     */
    suspend fun addServiceRequest(req: WifiP2pServiceRequest) = managerLock.withLock {
        managerInitializedState.waitForTrue()
        val operationResult = suspendCoroutine<Result<Unit>> { continuation ->
            wifiP2pManager.addServiceRequest(frameworkChannel, req, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    continuation.resume(Result.success(Unit))
                }

                override fun onFailure(reason: Int) {
                    continuation.resume(Result.failure(Exception("Failed to add service request: $reason")))
                }
            })
        }
        return@withLock operationResult.getOrThrow()
    }

    /**
     * @see WifiP2pManager.cancelConnect
     */
    suspend fun cancelConnect() = managerLock.withLock {
        managerInitializedState.waitForTrue()
        val operationResult = suspendCoroutine<Result<Unit>> { continuation ->
            wifiP2pManager.cancelConnect(frameworkChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    continuation.resume(Result.success(Unit))
                }

                override fun onFailure(reason: Int) {
                    continuation.resume(Result.failure(Exception("Failed to cancel connect: $reason")))
                }
            })
        }
        return@withLock operationResult.getOrThrow()
    }

    /**
     * @see WifiP2pManager.clearLocalServices
     */
    suspend fun clearLocalServices() = managerLock.withLock {
        managerInitializedState.waitForTrue()
        val operationResult = suspendCoroutine<Result<Unit>> { continuation ->
            wifiP2pManager.clearLocalServices(frameworkChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    continuation.resume(Result.success(Unit))
                }

                override fun onFailure(reason: Int) {
                    continuation.resume(Result.failure(Exception("Failed to clear local services: $reason")))
                }
            })
        }
        return@withLock operationResult.getOrThrow()
    }

    /**
     * @see WifiP2pManager.clearServiceRequests
     */
    suspend fun clearServiceRequests() = managerLock.withLock {
        managerInitializedState.waitForTrue()
        val operationResult = suspendCoroutine<Result<Unit>> { continuation ->
            wifiP2pManager.clearServiceRequests(frameworkChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    continuation.resume(Result.success(Unit))
                }

                override fun onFailure(reason: Int) {
                    continuation.resume(Result.failure(Exception("Failed to clear service requests: $reason")))
                }
            })
        }
        return@withLock operationResult.getOrThrow()
    }

    /**
     * @see WifiP2pManager.connect
     */
    @RequiresPermission(
        allOf = [
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_FINE_LOCATION
        ], conditional = true
    )
    suspend fun connect(config: WifiP2pConfig) = managerLock.withLock {
        managerInitializedState.waitForTrue()
        val operationResult = suspendCoroutine<Result<Unit>> { continuation ->
            wifiP2pManager.connect(frameworkChannel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    continuation.resume(Result.success(Unit))
                }

                override fun onFailure(reason: Int) {
                    continuation.resume(Result.failure(Exception("Failed to connect: $reason")))
                }
            })
        }
        updateConnectionInfo()
        updateGroupInfo()
        return@withLock operationResult.getOrThrow()
    }

    /**
     * @see WifiP2pManager.createGroup
     */
    @RequiresPermission(
        allOf = [
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_FINE_LOCATION
        ], conditional = true
    )
    suspend fun createGroup() = managerLock.withLock {
        managerInitializedState.waitForTrue()
        val operationResult = suspendCoroutine<Result<Unit>> { continuation ->
            wifiP2pManager.createGroup(frameworkChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    continuation.resume(Result.success(Unit))
                }

                override fun onFailure(reason: Int) {
                    continuation.resume(Result.failure(Exception("Failed to create group: $reason")))
                }
            })
        }
        updateGroupInfo()
        return@withLock operationResult.getOrThrow()
    }

    /**
     * Note: This is the only Public API on Android to create a WiFi AP network of any kind using a configuration.
     *
     * Offical SoftAPs like those created with [WifiManager.startLocalOnlyHotspot] are not configurable.
     *
     * @see WifiManager.startLocalOnlyHotspot
     * @see WifiManager.startLocalOnlyHotSpotSafe
     * @see WifiP2pManager.createGroup
     */
    @RequiresPermission(
        allOf = [
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_FINE_LOCATION
        ], conditional = true
    )
    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun createGroup(config: WifiP2pConfig) = managerLock.withLock {
        managerInitializedState.waitForTrue()
        val operationResult = suspendCoroutine<Result<Unit>> { continuation ->
            wifiP2pManager.createGroup(frameworkChannel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    continuation.resume(Result.success(Unit))
                }

                override fun onFailure(reason: Int) {
                    continuation.resume(Result.failure(Exception("Failed to create group: $reason")))
                }
            })
        }
        updateGroupInfo()
        return@withLock operationResult.getOrThrow()
    }

    /**
     * @see WifiP2pManager.discoverPeers
     */
    @RequiresPermission(
        allOf = [
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_FINE_LOCATION
        ], conditional = true
    )
    suspend fun discoverPeers() = managerLock.withLock {
        managerInitializedState.waitForTrue()
        val operationResult = suspendCoroutine<Result<Unit>> { continuation ->
            wifiP2pManager.discoverPeers(frameworkChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    continuation.resume(Result.success(Unit))
                }

                override fun onFailure(reason: Int) {
                    continuation.resume(Result.failure(Exception("Failed to discover peers: $reason")))
                }
            })
        }
        discoveryState.waitForTrue()
        return@withLock operationResult.getOrThrow()
    }

    /**
     * @see WifiP2pManager.discoverPeersOnSocialChannels
     */
    @RequiresPermission(
        allOf = [
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_FINE_LOCATION
        ], conditional = true
    )
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun discoverPeersOnSocialChannels() = managerLock.withLock {
        managerInitializedState.waitForTrue()
        val operationResult = suspendCoroutine<Result<Unit>> { continuation ->
            wifiP2pManager.discoverPeersOnSocialChannels(frameworkChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    continuation.resume(Result.success(Unit))
                }

                override fun onFailure(reason: Int) {
                    continuation.resume(Result.failure(Exception("Failed to discover peers on social channels: $reason")))
                }
            })
        }
        discoveryState.waitForTrue()
        return@withLock operationResult.getOrThrow()
    }

    /**
     * @see WifiP2pManager.discoverPeersOnSpecificFrequency
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @RequiresPermission(
        allOf = [
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_FINE_LOCATION
        ], conditional = true
    )
    suspend fun discoverPeersOnSpecificFrequency(frequency: Int) = managerLock.withLock {
        managerInitializedState.waitForTrue()
        val operationResult = suspendCoroutine<Result<Unit>> { continuation ->
            wifiP2pManager.discoverPeersOnSpecificFrequency(
                frameworkChannel,
                frequency,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        continuation.resume(Result.success(Unit))
                    }

                    override fun onFailure(reason: Int) {
                        continuation.resume(Result.failure(Exception("Failed to discover peers on specific frequency: $reason")))
                    }
                })
        }
        discoveryState.waitForTrue()
        return@withLock operationResult.getOrThrow()
    }

    /**
     * @see WifiP2pManager.discoverServices
     */
    @RequiresPermission(
        allOf = [
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_FINE_LOCATION
        ], conditional = true
    )
    suspend fun discoverServices() = managerLock.withLock {
        managerInitializedState.waitForTrue()
        val operationResult = suspendCoroutine<Result<Unit>> { continuation ->
            wifiP2pManager.discoverServices(frameworkChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    continuation.resume(Result.success(Unit))
                }

                override fun onFailure(reason: Int) {
                    continuation.resume(Result.failure(Exception("Failed to discover services: $reason")))
                }
            })
        }
        return@withLock operationResult.getOrThrow()
    }

    /**
     * @see WifiP2pManager.getListenState
     */
    @RequiresPermission(
        allOf = [
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_FINE_LOCATION
        ], conditional = true
    )
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    suspend fun getListenState(): Int = managerLock.withLock {
        managerInitializedState.waitForTrue()
        val operationResult = suspendCoroutine<Result<Int>> { continuation ->
            wifiP2pManager.getListenState(frameworkChannel, mainExecutor) { state ->
                continuation.resume(Result.success(state))
            }
        }
        return@withLock operationResult.getOrThrow()
    }

    /**
     * @see WifiP2pManager.removeClient
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun removeClient(peerAddress: MacAddress) = managerLock.withLock {
        managerInitializedState.waitForTrue()
        val operationResult = suspendCoroutine<Result<Unit>> { continuation ->
            wifiP2pManager.removeClient(frameworkChannel, peerAddress, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    continuation.resume(Result.success(Unit))
                }

                override fun onFailure(reason: Int) {
                    continuation.resume(Result.failure(Exception("Failed to remove client: $reason")))
                }
            })
        }
        return@withLock operationResult.getOrThrow()
    }

    /**
     * @see WifiP2pManager.removeGroup
     */
    suspend fun removeGroup() = managerLock.withLock {
        managerInitializedState.waitForTrue()
        val operationResult = suspendCoroutine<Result<Unit>> { continuation ->
            wifiP2pManager.removeGroup(frameworkChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    continuation.resume(Result.success(Unit))
                }

                override fun onFailure(reason: Int) {
                    continuation.resume(Result.failure(Exception("Failed to remove group: $reason")))
                }
            })
        }
        return@withLock operationResult.getOrThrow()
    }

    /**
     * @see WifiP2pManager.removeLocalService
     */
    suspend fun removeLocalService(servInfo: WifiP2pServiceInfo) = managerLock.withLock {
        managerInitializedState.waitForTrue()
        val operationResult = suspendCoroutine<Result<Unit>> { continuation ->
            wifiP2pManager.removeLocalService(frameworkChannel, servInfo, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    continuation.resume(Result.success(Unit))
                }

                override fun onFailure(reason: Int) {
                    continuation.resume(Result.failure(Exception("Failed to remove local service: $reason")))
                }
            })
        }
        return@withLock operationResult.getOrThrow()
    }

    /**
     * @see WifiP2pManager.removeServiceRequest
     */
    suspend fun removeServiceRequest(req: WifiP2pServiceRequest) = managerLock.withLock {
        managerInitializedState.waitForTrue()
        val operationResult = suspendCoroutine<Result<Unit>> { continuation ->
            wifiP2pManager.removeServiceRequest(frameworkChannel, req, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    continuation.resume(Result.success(Unit))
                }

                override fun onFailure(reason: Int) {
                    continuation.resume(Result.failure(Exception("Failed to remove service request: $reason")))
                }
            })
        }
        return@withLock operationResult.getOrThrow()
    }

    /**
     * @see connectionInfo
     * @see WifiP2pManager.requestConnectionInfo
     */
    suspend fun requestConnectionInfo(): WifiP2pInfo = managerLock.withLock {
        managerInitializedState.waitForTrue()
        val operationResult = suspendCoroutine { continuation ->
            wifiP2pManager.requestConnectionInfo(frameworkChannel) { info ->
                continuation.resume(Result.success(info))
            }
        }
        return@withLock operationResult.getOrThrow()
    }

    /**
     * @see WifiP2pManager.requestDiscoveryState
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun requestDiscoveryState(): Int = managerLock.withLock {
        managerInitializedState.waitForTrue()
        val operationResult = suspendCoroutine { continuation ->
            wifiP2pManager.requestDiscoveryState(frameworkChannel) { state ->
                continuation.resume(Result.success(state))
            }
        }
        return@withLock operationResult.getOrThrow()
    }

    /**
     * @see WifiP2pManager.requestGroupInfo
     */
    @RequiresPermission(
        allOf = [
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_FINE_LOCATION
        ], conditional = true
    )
    suspend fun requestGroupInfo(): WifiP2pGroup? = managerLock.withLock {
        managerInitializedState.waitForTrue()
        val operationResult = suspendCoroutine { continuation ->
            wifiP2pManager.requestGroupInfo(frameworkChannel) { info ->
                continuation.resume(Result.success(info))
            }
        }
        return@withLock operationResult.getOrThrow()
    }

    /**
     * @see WifiP2pManager.requestNetworkInfo
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun requestNetworkInfo(): NetworkInfo = managerLock.withLock {
        managerInitializedState.waitForTrue()
        val operationResult = suspendCoroutine { continuation ->
            wifiP2pManager.requestNetworkInfo(frameworkChannel) { info ->
                continuation.resume(Result.success(info))
            }
        }
        return@withLock operationResult.getOrThrow()
    }

    /**
     * @see peersFlow
     * @see WifiP2pManager.requestPeers
     */
    @RequiresPermission(
        allOf = [
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_FINE_LOCATION
        ], conditional = true
    )
    suspend fun requestPeers(): Collection<WifiP2pDevice> = managerLock.withLock {
        managerInitializedState.waitForTrue()
        val operationResult = suspendCoroutine { continuation ->
            wifiP2pManager.requestPeers(frameworkChannel) { peers ->
                continuation.resume(Result.success(peers.deviceList))
            }
        }
        return@withLock operationResult.getOrThrow()
    }

    /**
     * @see WifiP2pManager.setDnsSdResponseListeners
     */
    suspend fun setDnsSdResponseListeners(
        servListener: WifiP2pManager.DnsSdServiceResponseListener,
        txtListener: WifiP2pManager.DnsSdTxtRecordListener
    ) {
        managerInitializedState.waitForTrue()
        wifiP2pManager.setDnsSdResponseListeners(frameworkChannel, servListener, txtListener)
    }

    /**
     * @see WifiP2pManager.setServiceResponseListener
     */
    suspend fun setServiceResponseListener(
        listener: WifiP2pManager.ServiceResponseListener
    ) {
        managerInitializedState.waitForTrue()
        wifiP2pManager.setServiceResponseListener(frameworkChannel, listener)
    }

    /**
     * @see WifiP2pManager.setUpnpServiceResponseListener
     */
    suspend fun setUpnpServiceResponseListener(
        listener: WifiP2pManager.UpnpServiceResponseListener
    ) {
        managerInitializedState.waitForTrue()
        wifiP2pManager.setUpnpServiceResponseListener(frameworkChannel, listener)
    }

    /**
     * This probably shouldn't be called.
     *
     * @see WifiP2pManager.startListening
     */
    @RequiresPermission(
        allOf = [
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_FINE_LOCATION
        ], conditional = true
    )
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun startListening() = managerLock.withLock {
        managerInitializedState.waitForTrue()
        val operationResult = suspendCoroutine<Result<Unit>> { continuation ->
            wifiP2pManager.startListening(frameworkChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    continuation.resume(Result.success(Unit))
                }

                override fun onFailure(reason: Int) {
                    continuation.resume(Result.failure(Exception("Failed to start listening: $reason")))
                }
            })
        }
        return@withLock operationResult.getOrThrow()
    }

    /**
     * This probably shouldn't be called.
     *
     * @see WifiP2pManager.stopListening
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun stopListening() = managerLock.withLock {
        managerInitializedState.waitForTrue()
        val operationResult = suspendCoroutine<Result<Unit>> { continuation ->
            wifiP2pManager.stopListening(frameworkChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    continuation.resume(Result.success(Unit))
                }

                override fun onFailure(reason: Int) {
                    continuation.resume(Result.failure(Exception("Failed to stop listening: $reason")))
                }
            })
        }
        return@withLock operationResult.getOrThrow()
    }

    /**
     * This probably shouldn't be called.
     *
     * @see WifiP2pManager.stopPeerDiscovery
     */
    suspend fun stopPeerDiscovery() = managerLock.withLock {
        managerInitializedState.waitForTrue()
        val operationResult = suspendCoroutine<Result<Unit>> { continuation ->
            wifiP2pManager.stopPeerDiscovery(frameworkChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    continuation.resume(Result.success(Unit))
                }

                override fun onFailure(reason: Int) {
                    continuation.resume(Result.failure(Exception("Failed to stop peer discovery: $reason")))
                }
            })
        }
        return@withLock operationResult.getOrThrow()
    }

    // attributes

    /**
     * @see WifiP2pManager.isChannelConstrainedDiscoverySupported
     */
    val isChannelConstrainedDiscoverySupported: Boolean
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        get() = wifiP2pManager.isChannelConstrainedDiscoverySupported

    /**
     * @see WifiP2pManager.isGroupClientRemovalSupported
     */
    val isGroupClientRemovalSupported: Boolean
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        get() = wifiP2pManager.isGroupClientRemovalSupported

    /**
     * @see WifiP2pManager.isGroupOwnerIPv6LinkLocalAddressProvided
     */
    val isGroupOwnerIPv6LinkLocalAddressProvided: Boolean
        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        get() = wifiP2pManager.isGroupOwnerIPv6LinkLocalAddressProvided
}
