package com.vn.qrscanner.utils

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import com.vn.qrscanner.extensions.isLink
import com.vn.qrscanner.extensions.toWebUri

enum class WifiSecurityType {
    WPA,
    WEP,
    OPEN;

    companion object {
        fun fromString(type: String?): WifiSecurityType {
            if (type.isNullOrEmpty()) return OPEN
            val upper = type.uppercase().trim()
            return when {
                upper.contains("WPA") || upper.contains("WPA2") || upper.contains("WPA3") -> WPA
                upper.contains("WEP") -> WEP
                upper.contains("NOPASS") || upper.contains("OPEN") || upper == "NONE" -> OPEN
                else -> WPA
            }
        }
    }
}

sealed class QrCodeType {
    data class Wifi(
        val ssid: String,
        val password: String = "",
        val securityType: WifiSecurityType = WifiSecurityType.OPEN,
        val isHidden: Boolean = false,
        val rawData: String
    ) : QrCodeType()

    data class Url(val url: String) : QrCodeType()
    data class Text(val text: String) : QrCodeType()
}

object QrCodeUtils {

    fun detect(content: String): QrCodeType {
        val trimmed = content.trim()
        if (trimmed.startsWith("WIFI:", ignoreCase = true)) {
            val wifi = parseWifi(trimmed)
            if (wifi != null && wifi.ssid.isNotEmpty()) {
                return wifi
            }
        }
        if (trimmed.isLink()) {
            return QrCodeType.Url(trimmed)
        }
        return QrCodeType.Text(trimmed)
    }

    fun parseWifi(rawWifi: String): QrCodeType.Wifi? {
        if (!rawWifi.startsWith("WIFI:", ignoreCase = true)) return null
        val body = rawWifi.substring(5)

        var ssid = ""
        var password = ""
        var securityStr: String? = null
        var isHidden = false

        val segments = splitEscaped(body, ';')
        for (segment in segments) {
            val keyValue = splitEscaped(segment, ':', limit = 2)
            if (keyValue.size == 2) {
                val key = unescapeWifiField(keyValue[0].trim().uppercase())
                val value = unescapeWifiField(keyValue[1])
                when (key) {
                    "S" -> ssid = value
                    "P" -> password = value
                    "T" -> securityStr = value
                    "H" -> isHidden = value.equals("true", ignoreCase = true)
                }
            }
        }

        if (ssid.isEmpty()) return null

        val securityType = WifiSecurityType.fromString(securityStr)

        return QrCodeType.Wifi(
            ssid = ssid,
            password = password,
            securityType = if (password.isEmpty() && securityStr == null) WifiSecurityType.OPEN else securityType,
            isHidden = isHidden,
            rawData = rawWifi
        )
    }

    private fun splitEscaped(input: String, delimiter: Char, limit: Int = 0): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var isEscaped = false

        for (i in input.indices) {
            val ch = input[i]
            if (isEscaped) {
                sb.append(ch)
                isEscaped = false
            } else if (ch == '\\') {
                isEscaped = true
            } else if (ch == delimiter) {
                if (limit > 0 && result.size == limit - 1) {
                    sb.append(input.substring(i))
                    break
                }
                result.add(sb.toString())
                sb.clear()
            } else {
                sb.append(ch)
            }
        }

        if (sb.isNotEmpty() || result.isEmpty() || (input.isNotEmpty() && input.last() == delimiter)) {
            if (limit == 0 || result.size < limit) {
                result.add(sb.toString())
            }
        }

        return result
    }

    private fun unescapeWifiField(value: String): String {
        val sb = StringBuilder()
        var isEscaped = false
        for (ch in value) {
            if (isEscaped) {
                sb.append(ch)
                isEscaped = false
            } else if (ch == '\\') {
                isEscaped = true
            } else {
                sb.append(ch)
            }
        }
        var res = sb.toString()
        if (res.length >= 2 && res.startsWith("\"") && res.endsWith("\"")) {
            res = res.substring(1, res.length - 1)
        }
        return res
    }

    fun openUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toWebUri())
        context.startActivity(intent)
    }

    fun searchWeb(context: Context, query: String) {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
        }
        context.startActivity(intent)
    }
}
