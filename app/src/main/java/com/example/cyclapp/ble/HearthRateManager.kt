package com.example.cyclapp.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

class HeartRateManager(
    private val context: Context,
    private val onHeartRateReceived: (Int) -> Unit,
    private val onConnectionStateChanged: (String) -> Unit
) {
    private val TAG = "BLE_HR"
    private val mainHandler = Handler(Looper.getMainLooper())

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var connectedDevice: BluetoothDevice? = null
    private var isScanning = false
    private var isUserRequestedDisconnect = false

    private val HR_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val HR_MEASUREMENT_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: result.scanRecord?.deviceName ?: "Sensor HR"
            Log.d(TAG, "Device HR Ditemukan: $deviceName [${device.address}]")
            onConnectionStateChanged("Ditemukan: $deviceName")

            stopScan()
            connectToDevice(device)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan Gagal dengan error code: $errorCode")
            isScanning = false
            onConnectionStateChanged("Scan Gagal ($errorCode)")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Terhubung ke GATT server. Meminta High Connection Priority...")
                onConnectionStateChanged("Terhubung! Mengoptimalkan koneksi...")

                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)

                mainHandler.postDelayed({
                    if (bluetoothGatt != null) {
                        gatt.discoverServices()
                    }
                }, 300L)

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "Koneksi GATT terputus. Status code: $status")
                onConnectionStateChanged("Sinyal Terputus, Memulai ulang...")

                closeGatt()

                if (!isUserRequestedDisconnect) {
                    // Coba reconnect ke device yang pernah terhubung atau scan ulang
                    mainHandler.postDelayed({
                        if (!isUserRequestedDisconnect) {
                            connectedDevice?.let { device ->
                                connectToDevice(device)
                            } ?: startScan()
                        }
                    }, 2000L)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(HR_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(HR_MEASUREMENT_UUID)

                if (characteristic != null) {
                    Log.d(TAG, "HR Measurement Characteristic Ditemukan!")
                    gatt.setCharacteristicNotification(characteristic, true)

                    val descriptor = characteristic.getDescriptor(CCCD_UUID)
                    descriptor?.let {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeDescriptor(it, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            @Suppress("DEPRECATION")
                            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            gatt.writeDescriptor(it)
                        }
                    }
                    onConnectionStateChanged("Connected & Active")
                } else {
                    Log.e(TAG, "Service HR / Characteristic tidak ditemukan pada GATT server.")
                    onConnectionStateChanged("Service HR Tidak Lengkap")
                }
            } else {
                Log.e(TAG, "onServicesDiscovered gagal dengan status $status")
            }
        }

        @Deprecated("Used for older Android APIs")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == HR_MEASUREMENT_UUID) {
                @Suppress("DEPRECATION")
                val bpm = HeartRateParser.parse(characteristic.value)
                bpm?.let { onHeartRateReceived(it) }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == HR_MEASUREMENT_UUID) {
                val bpm = HeartRateParser.parse(value)
                bpm?.let { onHeartRateReceived(it) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            onConnectionStateChanged("Bluetooth HP Mati")
            return
        }

        if (isScanning) {
            Log.d(TAG, "Scan sudah berjalan.")
            return
        }

        isUserRequestedDisconnect = false
        onConnectionStateChanged("Memindai Sensor HR...")

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            onConnectionStateChanged("BLE Scanner tidak tersedia")
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // Pindai khusus perangkat yang memancarkan Service UUID Heart Rate (0x180D)
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(HR_SERVICE_UUID))
            .build()

        isScanning = true
        scanner.startScan(listOf(filter), settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (isScanning) {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            isScanning = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        connectedDevice = device
        onConnectionStateChanged("Menghubungkan...")

        closeGatt()

        bluetoothGatt = device.connectGatt(
            context,
            false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        bluetoothGatt?.let { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error saat menutup GATT", e)
            }
        }
        bluetoothGatt = null
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        isUserRequestedDisconnect = true
        stopScan()
        closeGatt()
        connectedDevice = null
        onConnectionStateChanged("Disconnected")
    }
}