package xyz.regulad.regulib.wifi

import android.annotation.SuppressLint
import android.net.ProxyInfo
import android.net.wifi.WifiConfiguration
import android.os.Build
import androidx.annotation.DeprecatedSinceApi
import java.net.InetAddress

/**
 * Utility method that performs multiple steps
 */
@Suppress("unused")
@SuppressLint("SoonBlockedPrivateApi", "DiscouragedPrivateApi", "PrivateApi")
@DeprecatedSinceApi(Build.VERSION_CODES.R) // doesn't work on R & up
fun WifiConfiguration.setStaticIp(gateway: String = "192.168.43.1", prefixLength: Int = 24) {
    // Get the IpAssignment class & STATIC enum value
    val ipAssignmentClass = Class.forName("android.net.IpConfiguration\$IpAssignment")
    val ipAssignmentStaticVal = ipAssignmentClass.getEnumConstants().first { it.toString() == "STATIC" }

    val proxySettingsClass = Class.forName("android.net.IpConfiguration\$ProxySettings")
    val proxySettingsStaticVal = proxySettingsClass.getEnumConstants().first { it.toString() == "NONE" }

    // Set IP address
    val linkAddressClass = Class.forName("android.net.LinkAddress")
    val inetAddress = InetAddress.getByName(gateway)
    val linkAddress = linkAddressClass.getConstructor(InetAddress::class.java, Integer.TYPE)
        .newInstance(inetAddress, prefixLength)

    // Get StaticIpConfiguration class and create instance
    val staticIpConfigClass = Class.forName("android.net.StaticIpConfiguration")
    val staticIpConfig = staticIpConfigClass.getConstructor().newInstance()
    // set ip address (LinkAddress)
    val ipAddressField = staticIpConfigClass.getDeclaredField("ipAddress")
    ipAddressField.isAccessible = true
    ipAddressField.set(staticIpConfig, linkAddress)
    // set gateway (InetAddress)
    val gatewayField = staticIpConfigClass.getDeclaredField("gateway")
    gatewayField.isAccessible = true
    gatewayField.set(staticIpConfig, inetAddress)
    // set dnsServers (ArrayList<InetAddress>)
    val dnsServersField = staticIpConfigClass.getDeclaredField("dnsServers")
    dnsServersField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val dnsServers = dnsServersField.get(staticIpConfig) as ArrayList<InetAddress>
    dnsServers.add(inetAddress)
    // set domains (String)
    val domainsField = staticIpConfigClass.getDeclaredField("domains")
    domainsField.isAccessible = true
    domainsField.set(staticIpConfig, "local")

    // create an IpConfiguration
    val ipConfigurationClass = Class.forName("android.net.IpConfiguration")
    val ipConfiguration = ipConfigurationClass.getConstructor().newInstance()
    // set ip assignment (IpAssignment)
    val ipAssignmentField = ipConfigurationClass.getDeclaredField("ipAssignment")
    ipAssignmentField.isAccessible = true
    ipAssignmentField.set(ipConfiguration, ipAssignmentStaticVal)
    // set proxy settings (ProxySettings)
    val proxySettingsField = ipConfigurationClass.getDeclaredField("proxySettings")
    proxySettingsField.isAccessible = true
    proxySettingsField.set(ipConfiguration, proxySettingsStaticVal)
    // setup static ip configuration (StaticIpConfiguration)
    val staticIpConfigurationField = ipConfigurationClass.getDeclaredField("staticIpConfiguration")
    staticIpConfigurationField.isAccessible = true
    staticIpConfigurationField.set(ipConfiguration, staticIpConfig)
    // httpProxy? (null by default)

    // Set the assignments in WifiConfiguration
    val setIpConfigurationMethod = this.javaClass.getDeclaredMethod("setIpConfiguration", ipConfigurationClass)
    setIpConfigurationMethod.invoke(this, ipConfiguration)

    val setStaticIpConfigurationMethod =
        this.javaClass.getDeclaredMethod("setStaticIpConfiguration", staticIpConfigClass)
    setStaticIpConfigurationMethod.invoke(this, staticIpConfig)

    val setIpAssignmentMethod = this.javaClass.getDeclaredMethod("setIpAssignment", ipAssignmentClass)
    setIpAssignmentMethod.invoke(this, ipAssignmentStaticVal)

    val setProxySettingsMethod = this.javaClass.getDeclaredMethod("setProxySettings", proxySettingsClass)
    setProxySettingsMethod.invoke(this, proxySettingsStaticVal)

    val setHttpProxyMethod = this.javaClass.getDeclaredMethod("setHttpProxy", ProxyInfo::class.java)
    setHttpProxyMethod.invoke(this, null)
}
