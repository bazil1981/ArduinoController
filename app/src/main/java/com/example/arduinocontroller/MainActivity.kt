package com.example.arduinocontroller

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.view.inputmethod.InputMethodManager
import com.google.android.material.textfield.TextInputEditText
import java.net.InetAddress

class MainActivity : AppCompatActivity() {

    private lateinit var ipInput: TextInputEditText
    private lateinit var portInput: TextInputEditText
    private lateinit var connectBtn: Button
    private lateinit var ledOnOffBtn: Button
    private lateinit var ledOnBtn: Button
    private lateinit var ledOffBtn: Button
    private lateinit var statusBtn: Button
    private lateinit var pwmSlider: SeekBar
    private lateinit var pwmValue: TextView
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var autoReconnectCheckbox: CheckBox
    private lateinit var saveSettingsBtn: Button
    private lateinit var tabLayout: com.google.android.material.tabs.TabLayout
    private lateinit var controlContainer: android.view.View
    private lateinit var settingsContainer: android.view.View

    private val PREFS_NAME = "ArduinoPrefs"
    private val KEY_IP = "arduino_ip"
    private val KEY_PORT = "arduino_port"
    private val KEY_AUTO_RECONNECT = "auto_reconnect"

    private var arduinoService: ArduinoService? = null
    private var isBound = false
    private var isConnected = false
    private var isLedOn = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ArduinoService.LocalBinder
            val serviceInstance = binder.getService()
            arduinoService = serviceInstance
            isBound = true
            
            // Load existing logs
            logText.text = ""
            serviceInstance.getLogs().forEach { log ->
                logText.append("$log\n")
            }
            mainHandler.post {
                val scrollView = logText.parent as? ScrollView
                scrollView?.fullScroll(ScrollView.FOCUS_DOWN)
            }

            serviceInstance.setListeners(
                onResponse = { response -> handleResponse(response) },
                onStatus = { connected -> updateConnectionStatus(connected) }
            )
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            arduinoService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        
        checkNotificationPermission()
        
        // Start and bind to service
        val intent = Intent(this, ArduinoService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun initViews() {
        ipInput = findViewById(R.id.ipAddress)
        portInput = findViewById(R.id.portNumber)
        connectBtn = findViewById(R.id.connectBtn)
        ledOnOffBtn = findViewById(R.id.ledOnOffBtn)
        ledOnBtn = findViewById(R.id.ledOnBtn)
        ledOffBtn = findViewById(R.id.ledOffBtn)
        statusBtn = findViewById(R.id.statusBtn)
        pwmSlider = findViewById(R.id.pwmSlider)
        pwmValue = findViewById(R.id.pwmValue)
        statusText = findViewById(R.id.statusText)
        logText = findViewById(R.id.logText)
        autoReconnectCheckbox = findViewById(R.id.autoReconnectCheckbox)
        saveSettingsBtn = findViewById(R.id.saveSettingsBtn)
        tabLayout = findViewById(R.id.tabLayout)
        controlContainer = findViewById(R.id.controlContainer)
        settingsContainer = findViewById(R.id.settingsContainer)
        
        loadSettings()
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ipInput.setText(prefs.getString(KEY_IP, getString(R.string.ip)))
        portInput.setText(prefs.getInt(KEY_PORT, getString(R.string.port).toInt()).toString())
        autoReconnectCheckbox.isChecked = prefs.getBoolean(KEY_AUTO_RECONNECT, true)
    }

    private fun saveSettings() {
        val ip = ipInput.text.toString()
        val port = portInput.text.toString().toIntOrNull() ?: 8888
        val autoReconnect = autoReconnectCheckbox.isChecked

        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_IP, ip)
            putInt(KEY_PORT, port)
            putBoolean(KEY_AUTO_RECONNECT, autoReconnect)
            apply()
        }
        Toast.makeText(this, "Settings Saved", Toast.LENGTH_SHORT).show()
        addLog("Settings saved: $ip:$port (Auto: $autoReconnect)")
    }

    private fun setupListeners() {
        tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        controlContainer.visibility = android.view.View.VISIBLE
                        settingsContainer.visibility = android.view.View.GONE
                        // Hide keyboard when switching back to control tab
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(tabLayout.windowToken, 0)
                    }
                    1 -> {
                        controlContainer.visibility = android.view.View.GONE
                        settingsContainer.visibility = android.view.View.VISIBLE
                        // Request focus on IP input when switching to settings
                        ipInput.postDelayed({
                            ipInput.requestFocus()
                            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.showSoftInput(ipInput, InputMethodManager.SHOW_IMPLICIT)
                        }, 100)
                    }
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })

        saveSettingsBtn.setOnClickListener {
            saveSettings()
            // Hide keyboard after saving
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(saveSettingsBtn.windowToken, 0)
        }

        // Force keyboard to appear on click/focus
        val forceShowKeyboard = { v: android.view.View ->
            v.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(v, InputMethodManager.SHOW_FORCED)
        }

        ipInput.setOnClickListener { forceShowKeyboard(it) }
        portInput.setOnClickListener { forceShowKeyboard(it) }
        
        ipInput.onFocusChangeListener = android.view.View.OnFocusChangeListener { v, hasFocus ->
            if (hasFocus) forceShowKeyboard(v)
        }
        portInput.onFocusChangeListener = android.view.View.OnFocusChangeListener { v, hasFocus ->
            if (hasFocus) forceShowKeyboard(v)
        }

        connectBtn.setOnClickListener {
            if (!isConnected) {
                val ip = ipInput.text.toString()
                val port = portInput.text.toString().toIntOrNull() ?: 8888
                arduinoService?.connect(ip, port)
            } else {
                disconnectFromArduino()
            }
        }

        ledOnOffBtn.setOnClickListener {
            if (isLedOn) {
                sendCommand("LED_OFF")
            } else {
                sendCommand("LED_ON")
            }
        }

        ledOnBtn.setOnClickListener { sendCommand("LED_ON") }
        ledOffBtn.setOnClickListener { sendCommand("LED_OFF") }
        statusBtn.setOnClickListener { sendCommand("STATUS") }

        pwmSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                pwmValue.text = getString(R.string.pwm_value, progress)
                if (fromUser) {
                    sendCommand("PWM:$progress")
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun sendCommand(command: String) {
        if (!isConnected) {
            addLog("Not connected!")
            return
        }
        addLog("Sent: $command")
        arduinoService?.sendCommand(command)
    }

    private fun handleResponse(response: String) {
        addLog("Response: $response")
        parseStateFromResponse(response)
        when {
            response.contains("STATUS") -> statusText.text = response
            response.contains("LED_ON") -> statusText.text = getString(R.string.status_led_on)
            response.contains("LED_OFF") -> statusText.text = getString(R.string.status_led_off)
            else -> statusText.text = getString(R.string.logs_response, response)
        }
        updateUI(isConnected)
    }

    private fun disconnectFromArduino() {
        // Since it's a service, we just update local state or stop service if needed
        isConnected = false
        updateUI(false)
        addLog("Disconnected")
        statusText.text = getString(R.string.logs_disconnected)
    }

    private fun parseStateFromResponse(response: String) {
        val clean = response.trim().uppercase()
        val previousState = isLedOn
        
        // Comprehensive check for ON state
        if (clean.contains("LED_ON") || 
            clean.contains("LED:ON") || 
            clean.contains("LED: ON") || 
            clean.contains("LED IS ON") ||
            (clean.contains("LED") && clean.contains("ON")) ||
            clean == "ON" || 
            clean.endsWith("ON")) {
            isLedOn = true
        } 
        // Comprehensive check for OFF state
        else if (clean.contains("LED_OFF") || 
                 clean.contains("LED:OFF") || 
                 clean.contains("LED: OFF") || 
                 clean.contains("LED IS OFF") ||
                 (clean.contains("LED") && clean.contains("OFF")) ||
                 clean == "OFF" || 
                 clean.endsWith("OFF")) {
            isLedOn = false
        }
        
        if (previousState != isLedOn) {
            addLog("Internal state updated: LED is now ${if (isLedOn) "ON" else "OFF"}")
        }
    }

    private fun updateConnectionStatus(connected: Boolean) {
        isConnected = connected
        updateUI(connected)
        if (connected) {
            statusText.text = getString(R.string.status_connected_to, ipInput.text)
        } else {
            statusText.text = getString(R.string.status_disconnected)
        }
    }

    private fun updateUI(connected: Boolean) {
        ledOnOffBtn.isEnabled = connected
        ledOnOffBtn.text = if (isLedOn) "LED ON" else "LED OFF"

        ledOnBtn.isEnabled = connected
        ledOffBtn.isEnabled = connected
        statusBtn.isEnabled = connected
        pwmSlider.isEnabled = connected
        connectBtn.text = if (connected) "Disconnect" else "Connect"
    }

    private fun addLog(message: String) {
        mainHandler.post {
            logText.append("${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())} - $message\n")
            val scrollView = logText.parent as? ScrollView
            scrollView?.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}