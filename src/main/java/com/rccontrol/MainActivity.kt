package com.rccontrol

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException
import java.io.OutputStream
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var isConnected = false
    private var currentMode = "Manual"
    private val MODE_COMMAND = "MODE:"
    private val DEVICE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        checkBluetoothSupport()
        requestPermissions()
        setupUI()
    }

    private fun checkBluetoothSupport() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }
    }

    private fun requestPermissions() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != 
            PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_LOCATION_PERMISSION -> {
                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Location permission required for Bluetooth", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupUI() {
        // Mode switch listener
        modeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isConnected) {
                currentMode = if (isChecked) "Auto" else "Manual"
                sendCommand("$MODE_COMMAND$currentMode")
                updateUIMode()
                vibrate(100)
            } else {
                Toast.makeText(this, "Connect to device first", Toast.LENGTH_SHORT).show()
                modeSwitch.isChecked = !isChecked
            }
        }

        // Control button listeners
        btnForward.setOnClickListener { if (currentMode == "Manual") sendCommand("F") }
        btnBackward.setOnClickListener { if (currentMode == "Manual") sendCommand("B") }
        btnLeft.setOnClickListener { if (currentMode == "Manual") sendCommand("L") }
        btnRight.setOnClickListener { if (currentMode == "Manual") sendCommand("R") }
        btnStop.setOnClickListener { sendCommand("S") }
    }

    private fun updateUIMode() {
        runOnUiThread {
            if (currentMode == "Auto") {
                modeOverlay.visibility = android.view.View.VISIBLE
                modeText.text = "AUTO MODE ACTIVE"
                disableManualControls()
            } else {
                modeOverlay.visibility = android.view.View.GONE
                enableManualControls()
            }
            Toast.makeText(this, "Switched to $currentMode mode", Toast.LENGTH_SHORT).show()
        }
    }

    private fun disableManualControls() {
        btnForward.isEnabled = false
        btnBackward.isEnabled = false
        btnLeft.isEnabled = false
        btnRight.isEnabled = false
    }

    private fun enableManualControls() {
        btnForward.isEnabled = true
        btnBackward.isEnabled = true
        btnLeft.isEnabled = true
        btnRight.isEnabled = true
    }

    private fun sendCommand(command: String) {
        try {
            outputStream?.write(command.toByteArray())
        } catch (e: IOException) {
            Toast.makeText(this, "Command failed", Toast.LENGTH_SHORT).show()
            disconnectBluetooth()
        }
    }

    private fun vibrate(duration: Long) {
        val vibrator = ContextCompat.getSystemService(this, Vibrator::class.java)
        vibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun connectToDevice(device: BluetoothDevice) {
        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(DEVICE_UUID)
            bluetoothSocket?.connect()
            outputStream = bluetoothSocket?.outputStream
            isConnected = true
            runOnUiThread {
                tvConnectionStatus.text = "Connected to ${device.name}"
                Toast.makeText(this, "Connection successful", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            runOnUiThread {
                Toast.makeText(this, "Connection failed", Toast.LENGTH_SHORT).show()
            }
            disconnectBluetooth()
        }
    }

    private fun disconnectBluetooth() {
        try {
            bluetoothSocket?.close()
            outputStream?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        isConnected = false
        runOnUiThread {
            tvConnectionStatus.text = "Disconnected"
            modeSwitch.isChecked = false
            currentMode = "Manual"
            updateUIMode()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            val device = data?.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            device?.let { connectToDevice(it) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectBluetooth()
    }

    companion object {
        private const val REQUEST_ENABLE_BT = 1
    }
}