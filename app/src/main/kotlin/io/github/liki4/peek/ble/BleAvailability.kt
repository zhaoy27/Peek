package io.github.liki4.peek.ble

import android.bluetooth.BluetoothManager
import android.content.Context

/**
 * Exception-safe BLE availability checks.
 *
 * Turning Bluetooth off (or revoking BLUETOOTH_CONNECT) makes
 * `adapter.bluetoothLeScanner` return null; the scanners used to `throw` from
 * inside a `callbackFlow`, and because those flows were collected from a
 * composition-scoped coroutine the exception escaped to the default handler and
 * killed the process. Every scan site now checks this first and shows a hint
 * instead of letting the app die.
 */
object BleAvailability {

    /** True when the phone has a BT adapter and the user has switched it on. */
    fun isBluetoothOn(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter?.isEnabled == true
    }.getOrDefault(false)

    /** User-facing explanation for an error thrown while scanning or connecting. */
    fun friendlyMessage(context: Context, error: Throwable): String = when {
        error is SecurityException ->
            "缺少蓝牙权限，请到系统设置里允许「附近的设备」权限后重试"
        !isBluetoothOn(context) ->
            "蓝牙未开启。请打开系统蓝牙后点「重试」"
        else ->
            "蓝牙操作失败：${error.message ?: error::class.simpleName}"
    }
}
