package com.mobileserver.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.IBinder
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment

class ServerStatusFragment : Fragment() {

    private lateinit var serverStatusText: TextView
    private lateinit var ipAddressText: TextView
    private lateinit var portText: TextView
    private lateinit var startStopButton: Button
    private lateinit var qrCodeImageView: ImageView
    
    private var httpServerService: HttpServerService? = null
    private var serviceBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as HttpServerService.LocalBinder
            httpServerService = binder.getService()
            serviceBound = true
            updateUI()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            serviceBound = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_server_status, container, false)
        
        serverStatusText = view.findViewById(R.id.serverStatusText)
        ipAddressText = view.findViewById(R.id.ipAddressText)
        portText = view.findViewById(R.id.portText)
        startStopButton = view.findViewById(R.id.startStopButton)
        qrCodeImageView = view.findViewById(R.id.qrCodeImageView)
        
        setupButtons()
        
        return view
    }
    
    override fun onStart() {
        super.onStart()
        Intent(requireContext(), HttpServerService::class.java).also { intent ->
            requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }
    
    override fun onStop() {
        super.onStop()
        if (serviceBound) {
            requireContext().unbindService(serviceConnection)
            serviceBound = false
        }
    }
    
    private fun setupButtons() {
        startStopButton.setOnClickListener {
            if (httpServerService?.isServerRunning() == true) {
                httpServerService?.stopServer()
                Toast.makeText(requireContext(), "Server stopped", Toast.LENGTH_SHORT).show()
            } else {
                val started = httpServerService?.startServer() ?: false
                if (started) {
                    Toast.makeText(requireContext(), "Server started", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Failed to start server", Toast.LENGTH_SHORT).show()
                }
            }
            updateUI()
        }
    }
    
    private fun updateUI() {
        val isRunning = httpServerService?.isServerRunning() ?: false
        
        serverStatusText.text = if (isRunning) "Server: Running" else "Server: Stopped"
        serverStatusText.setTextColor(if (isRunning) 
            requireContext().getColor(android.R.color.holo_green_dark) 
            else requireContext().getColor(android.R.color.holo_red_dark))
        
        startStopButton.text = if (isRunning) "Stop Server" else "Start Server"
        
        if (isRunning) {
            val port = httpServerService?.getServerPort() ?: 8080
            val ipAddress = getLocalIpAddress()
            
            portText.text = "Port: $port"
            ipAddressText.text = "IP: $ipAddress"
            
            // Show URL for easy access
            val serverUrl = "http://$ipAddress:$port"
            Toast.makeText(requireContext(), "Server running at: $serverUrl", Toast.LENGTH_LONG).show()
        } else {
            portText.text = "Port: --"
            ipAddressText.text = "IP: --"
        }
    }
    
    private fun getLocalIpAddress(): String {
        return try {
            val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipAddress = wifiManager.connectionInfo.ipAddress
            if (ipAddress != 0) {
                Formatter.formatIpAddress(ipAddress)
            } else {
                "192.168.1.x"  // Fallback when IP not available
            }
        } catch (e: Exception) {
            "192.168.1.x"  // Fallback on error
        }
    }
}