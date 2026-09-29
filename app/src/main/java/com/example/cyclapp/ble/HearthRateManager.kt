package com.example.cyclapp.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
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

    private val HR_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val HR_MEASUREMENT_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            Log.d(TAG, "Device HR Ditemukan: ${device.name ?: "Unknown"} [${device.address}]")
            onConnectionStateChanged("Ditemukan: ${device.name ?: "Sensor HR"}")

            stopScan()
            connectToDevice(device)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan Gagal dengan error code: $errorCode")
            onConnectionStateChanged("Scan Gagal ($errorCode)")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Terhubung ke GATT server. Meminta High Connection Priority...")
                onConnectionStateChanged("Terhubung! Mengoptimalkan koneksi...")

                // 1. Minta High Priority agar data dikirim cepat dan tidak putus-putus
                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)

                // Beri jeda sedikit (300ms) sebelum discoverServices agar chip BLE HP stabil
                mainHandler.postDelayed({
                    gatt.discoverServices()
                }, 300L)

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "Koneksi terputus. Mengatur reconnect...")
                onConnectionStateChanged("Sinyal Terputus, Memulai ulang...")

                gatt.close()
                bluetoothGatt = null

                // 2. Auto Reconnect jika terputus tiba-tiba
                connectedDevice?.let { device ->
                    mainHandler.postDelayed({
                        connectToDevice(device)
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
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeDescriptor(it, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(it)
                        }
                    }
                    onConnectionStateChanged("Connected & Active")
                } else {
                    onConnectionStateChanged("Service HR Tidak Lengkap")
                }
            }
        }

        @Deprecated("Used for older Android APIs")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == HR_MEASUREMENT_UUID) {
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

        onConnectionStateChanged("Memindai Sensor HR...")

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // Pindai tanpa ScanFilter terlebih dahulu untuk memastikan sensor tertangkap
        scanner?.startScan(null, settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        connectedDevice = device
        onConnectionStateChanged("Menghubungkan...")

        // Batalkan gatt lama jika masih ada
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null

        // Sambung ulang menggunakan TRANSPORT_LE
        bluetoothGatt = device.connectGatt(
            context,
            false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        connectedDevice = null
        bluetoothGatt?.let { gatt ->
            gatt.disconnect()
            gatt.close()
        }
        bluetoothGatt = null
        onConnectionStateChanged("Disconnected")
    }
}