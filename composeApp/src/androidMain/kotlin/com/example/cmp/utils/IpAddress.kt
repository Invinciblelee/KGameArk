package com.example.cmp.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 【最终正确实现】获取设备在局域网中的IP地址（通常是Wi-Fi的IPv4地址）。
 *
 * 这个实现通过遍历设备的所有网络接口来查找有效的、非环回的IPv4地址，
 * 这是在Android上获取局域网IP最可靠的方法。
 *
 * @return 设备的局域网IP地址字符串，如果找不到则返回 "0.0.0.0"。
 */
suspend fun getDeviceIpAddress(): String = withContext(Dispatchers.IO) {
    try {
        // 获取本机的所有网络接口的枚举
        val networkInterfaces = NetworkInterface.getNetworkInterfaces()
        // 遍历所有网络接口
        while (networkInterfaces.hasMoreElements()) {
            val networkInterface = networkInterfaces.nextElement()
            // 过滤掉非活动的、虚拟的以及环回接口，这些都不是我们想要的
            if (!networkInterface.isUp || networkInterface.isLoopback || networkInterface.isVirtual) {
                continue
            }

            // 获取当前网络接口上绑定的所有IP地址的枚举
            val inetAddresses = networkInterface.inetAddresses
            // 遍历所有IP地址
            while (inetAddresses.hasMoreElements()) {
                val inetAddress = inetAddresses.nextElement()
                // 确保它是一个IPv4地址，并且不是环回地址 (127.0.0.1)
                if (inetAddress is Inet4Address && !inetAddress.isLoopbackAddress) {
                    // 找到了！这就是我们需要的局域网IP地址，直接返回
                    return@withContext inetAddress.hostAddress ?: "0.0.0.0"
                }
            }
        }
    } catch (e: Exception) {
        // 在发生任何网络异常时打印堆栈信息，并返回一个默认值
        e.printStackTrace()
    }
    // 如果遍历完所有接口都没有找到符合条件的IP地址，返回默认值
    return@withContext "0.0.0.0"
}
