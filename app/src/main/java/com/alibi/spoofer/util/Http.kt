package com.alibi.spoofer.util

import com.alibi.spoofer.BuildConfig
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/** Why a network call failed, so the UI can say something useful. */
enum class NetworkError { OFFLINE, TIMEOUT, RATE_LIMITED, SERVER, BAD_RESPONSE }

sealed interface HttpResult {
    data class Ok(val body: String) : HttpResult
    data class Failed(val error: NetworkError) : HttpResult
}

object Http {
    /**
     * Identifies the app to the OpenStreetMap services (tiles, Nominatim, Photon, OSRM),
     * as their usage policies require.
     */
    val USER_AGENT = "Alibi/${BuildConfig.VERSION_NAME} (+https://github.com/dani-uiu/alibi)"

    private const val TIMEOUT_MS = 8000

    /**
     * Blocking GET. Call from [kotlinx.coroutines.Dispatchers.IO].
     */
    fun get(url: String, acceptLanguage: String? = null): HttpResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                if (acceptLanguage != null) setRequestProperty("Accept-Language", acceptLanguage)
            }
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK ->
                    HttpResult.Ok(connection.inputStream.bufferedReader().use { it.readText() })
                429, 509 -> HttpResult.Failed(NetworkError.RATE_LIMITED)
                else -> HttpResult.Failed(if (code >= 500) NetworkError.SERVER else NetworkError.BAD_RESPONSE)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: UnknownHostException) {
            HttpResult.Failed(NetworkError.OFFLINE)
        } catch (_: SocketTimeoutException) {
            HttpResult.Failed(NetworkError.TIMEOUT)
        } catch (_: IOException) {
            HttpResult.Failed(NetworkError.OFFLINE)
        } catch (_: Exception) {
            HttpResult.Failed(NetworkError.BAD_RESPONSE)
        } finally {
            connection?.disconnect()
        }
    }
}
