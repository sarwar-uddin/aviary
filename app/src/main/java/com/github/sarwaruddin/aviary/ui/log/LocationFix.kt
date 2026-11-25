package com.github.sarwaruddin.aviary.ui.log

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Plain android.location: no Google Play services. */

internal val LocationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

internal fun hasLocationPermission(context: Context): Boolean = LocationPermissions.any {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

/**
 * A current position fix, or null. Uses a recent last-known fix if there is one,
 * otherwise races the GPS and network providers for a fresh fix, and finally
 * falls back to any last-known fix.
 */
@SuppressLint("MissingPermission")
internal suspend fun currentFix(context: Context, timeoutMs: Long = 20_000): Location? {
    if (!hasLocationPermission(context)) return null
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter {
        runCatching { lm.isProviderEnabled(it) }.getOrDefault(false)
    }
    val lastKnown = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
    if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < 2 * 60_000) return lastKnown
    if (providers.isEmpty()) return lastKnown

    val fresh = withTimeoutOrNull(timeoutMs) {
        coroutineScope {
            val results = Channel<Location?>(providers.size)
            val jobs = providers.map { p -> launch { results.send(singleFix(lm, p, context)) } }
            var got: Location? = null
            var received = 0
            while (got == null && received < providers.size) {
                got = results.receive()
                received++
            }
            jobs.forEach { it.cancel() }
            got
        }
    }
    return fresh ?: lastKnown
}

@SuppressLint("MissingPermission")
private suspend fun singleFix(lm: LocationManager, provider: String, context: Context): Location? =
    suspendCancellableCoroutine { cont ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            try {
                lm.getCurrentLocation(provider, signal, context.mainExecutor) { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        } else {
            // Implement every method: on API < 30 they are abstract in the framework.
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (cont.isActive) cont.resume(location)
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {
                    if (cont.isActive) cont.resume(null)
                }
            }
            cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
            try {
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }
