package com.example.usb_demo

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import com.felhr.usbserial.UsbSerialDevice
import io.flutter.embedding.android.FlutterActivity
import kotlinx.coroutines.Job
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.felhr.usbserial.UsbSerialInterface
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity: FlutterActivity(){
    private val CHANNEL = "usb_serial_channel"
    private val ACTION_USB_PERMISSION = "com.example.usb_demo.USB_PERMISSION"
    private var serialPort: UsbSerialDevice? = null
    private var connectionJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val usbManager = getSystemService(USB_SERVICE) as UsbManager

        flutterEngine?.dartExecutor?.binaryMessenger?.let {
            MethodChannel(it, CHANNEL).setMethodCallHandler { call, result ->
                when (call.method) {
                    "requestUsbPermission" -> {
                        val deviceList = usbManager.deviceList
                        if (deviceList.isNotEmpty()) {
                            val device = deviceList.values.first() // Customize selection logic as needed
                            if (!usbManager.hasPermission(device)) {
                                val permissionIntent = PendingIntent.getActivity(
                                    this, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE
                                )
                                usbManager.requestPermission(device, permissionIntent)
                                result.success("Requesting permission for USB device")
                            } else {
                                result.success("Permission already granted")
                            }
                        } else {
                            result.error("No Device", "No USB device connected", null)
                        }
                    }

                    "startSerialStream" -> {
                        connectionJob = CoroutineScope(Dispatchers.IO).launch {
                            getSerialDataStream(usbManager).collect { data ->
                                withContext(Dispatchers.Main) {
                                    MethodChannel(flutterEngine?.dartExecutor?.binaryMessenger!!, CHANNEL)
                                        .invokeMethod("onDataReceived", data)
                                }
                            }
                        }
                        result.success("Streaming started")
                    }

                    "stopSerialStream" -> {
                        connectionJob?.cancel()
                        serialPort?.close()
                        result.success("Streaming stopped")
                    }

                    else -> result.notImplemented()
                }
            }
        }
    }

    private fun getSerialDataStream(usbManager: UsbManager): Flow<String> = callbackFlow {
        val deviceList = usbManager.deviceList
        if (deviceList.isNotEmpty()) {
            val device = deviceList.values.first()

            if (!usbManager.hasPermission(device)) {
                trySend("No permission to access the device")
                close()
                return@callbackFlow
            }

            val connection = usbManager.openDevice(device)
            serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection)

            if (serialPort != null && serialPort!!.open()) {
                serialPort!!.setBaudRate(9600)
                serialPort!!.setDataBits(UsbSerialInterface.DATA_BITS_8)
                serialPort!!.setStopBits(UsbSerialInterface.STOP_BITS_1)
                serialPort!!.setParity(UsbSerialInterface.PARITY_NONE)
                serialPort!!.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF)

                trySend("Device connected successfully")

                serialPort!!.read { data ->
                    val receivedData = String(data)
                    trySend(receivedData)
                }
            } else {
                trySend("Failed to open the serial port")
                connection?.close()
            }
        } else {
            trySend("No USB device connected")
        }

        awaitClose { serialPort?.close() }
    }
}