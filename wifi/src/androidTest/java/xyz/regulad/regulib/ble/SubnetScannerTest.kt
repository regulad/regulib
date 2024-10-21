package xyz.regulad.regulib.ble

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.apache.commons.net.util.SubnetUtils
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import xyz.regulad.regulib.wifi.SubnetScanner
import java.net.InetAddress

@RunWith(AndroidJUnit4::class)
class SubnetScannerTest {

    private lateinit var subnetScanner: SubnetScanner
    private lateinit var mockSubnetInfo: SubnetUtils.SubnetInfo

    @Before
    fun setUp() {
        mockSubnetInfo = SubnetUtils("192.168.0.0/24").info
    }

    @After
    fun tearDown() {
        if (::subnetScanner.isInitialized) {
            subnetScanner.stopScanning()
        }
    }

    @Test
    fun testScanWithAllReachable() = runTest {
        val mockAssessor: suspend (InetAddress) -> Boolean = {
            Log.d("SubnetScannerTest", "Assessing $it")
            true
        }
        subnetScanner = SubnetScanner(mockSubnetInfo, mockAssessor)

        subnetScanner.reachableAddresses.first { it.size >= mockSubnetInfo.addressCount }
    }

    @Test
    fun testScanWithNoneReachable() = runTest {
        val mockAssessor: suspend (InetAddress) -> Boolean = {
            Log.d("SubnetScannerTest", "Assessing $it")
            false
        }
        subnetScanner = SubnetScanner(mockSubnetInfo, mockAssessor)

        val reachableAddresses = subnetScanner.reachableAddresses.first()
        assertTrue(reachableAddresses.isEmpty())
    }

    @Test
    fun testScanWithSomeReachable() = runTest {
        val mockAssessor: suspend (InetAddress) -> Boolean = { address ->
            Log.d("SubnetScannerTest", "Assessing $address")
            address.hostAddress == "192.168.0.2"
        }
        subnetScanner = SubnetScanner(mockSubnetInfo, mockAssessor)

        val reachableAddresses = subnetScanner.reachableAddresses.first { it.size == 1 }
        assertTrue(reachableAddresses.contains(InetAddress.getByName("192.168.0.2")))
    }
}
