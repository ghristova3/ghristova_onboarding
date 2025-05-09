package com.example.abaltachat.utils

import java.net.Inet4Address
import java.net.NetworkInterface

fun getLocalIpAddress(): String? {
    val interfaces = NetworkInterface.getNetworkInterfaces()
    for (item in interfaces) {
        val addresses = item.inetAddresses
        for (address in addresses) {
            if (!address.isLoopbackAddress && address is Inet4Address) {
                return address.hostAddress
            }
        }
    }
    return null
}