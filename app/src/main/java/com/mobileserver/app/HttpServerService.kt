package com.mobileserver.app

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

class HttpServerService : Service() {
    
    private val binder = LocalBinder()
    private val executor = Executors.newCachedThreadPool()
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val gson = Gson()
    private var codeExecutionService: CodeExecutionService? = null
    
    companion object {
        private const val TAG = "HttpServerService"
        private const val DEFAULT_PORT = 8080
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): HttpServerService = this@HttpServerService
    }
    
    override fun onCreate() {
        super.onCreate()
        codeExecutionService = CodeExecutionService()
        Log.d(TAG, "HttpServerService created")
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    fun startServer(port: Int = DEFAULT_PORT): Boolean {
        if (isRunning) {
            Log.w(TAG, "Server already running")
            return false
        }
        
        return try {
            serverSocket = ServerSocket()
            serverSocket?.bind(InetSocketAddress(port))
            isRunning = true
            
            executor.submit {
                Log.i(TAG, "HTTP Server started on port $port")
                acceptConnections()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server: ${e.message}")
            false
        }
    }
    
    fun stopServer() {
        isRunning = false
        serverSocket?.close()
        Log.i(TAG, "HTTP Server stopped")
    }
    
    fun isServerRunning(): Boolean = isRunning
    
    fun getServerPort(): Int = serverSocket?.localPort ?: -1
    
    private fun acceptConnections() {
        while (isRunning && serverSocket?.isClosed == false) {
            try {
                val clientSocket = serverSocket?.accept()
                clientSocket?.let { socket ->
                    executor.submit { handleClient(socket) }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "Error accepting connection: ${e.message}")
                }
            }
        }
    }
    
    private fun handleClient(socket: Socket) {
        try {
            val input = socket.getInputStream().bufferedReader()
            val output = socket.getOutputStream()
            
            val requestLine = input.readLine()
            if (requestLine == null) {
                socket.close()
                return
            }
            
            val parts = requestLine.split(" ")
            if (parts.size < 3) {
                sendResponse(output, 400, "Bad Request")
                socket.close()
                return
            }
            
            val method = parts[0]
            val path = parts[1]
            
            // Read headers
            val headers = mutableMapOf<String, String>()
            var line = input.readLine()
            while (line != null && line.isNotEmpty()) {
                val colonIndex = line.indexOf(':')
                if (colonIndex > 0) {
                    val key = line.substring(0, colonIndex).trim()
                    val value = line.substring(colonIndex + 1).trim()
                    headers[key.lowercase()] = value
                }
                line = input.readLine()
            }
            
            // Read body if present
            var body = ""
            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            if (contentLength > 0) {
                val bodyChars = CharArray(contentLength)
                input.read(bodyChars, 0, contentLength)
                body = String(bodyChars)
            }
            
            handleRequest(output, method, path, headers, body)
            socket.close()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client: ${e.message}")
            try {
                socket.close()
            } catch (closeException: Exception) {
                Log.e(TAG, "Error closing socket: ${closeException.message}")
            }
        }
    }
    
    private fun handleRequest(output: java.io.OutputStream, method: String, path: String, headers: Map<String, String>, body: String) {
        when {
            method == "GET" && path == "/" -> handleIndex(output)
            method == "GET" && path == "/status" -> handleStatus(output)
            method == "POST" && path == "/execute" -> handleExecute(output, body)
            method == "GET" && path == "/processes" -> handleProcesses(output)
            method == "DELETE" && path.startsWith("/processes/") -> handleStopProcess(output, path)
            method == "OPTIONS" -> handleOptions(output)
            else -> sendResponse(output, 404, "Not Found")
        }
    }
    
    private fun handleIndex(output: java.io.OutputStream) {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Mobile Code Server</title>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                    body { font-family: Arial, sans-serif; margin: 20px; }
                    .container { max-width: 800px; margin: 0 auto; }
                    textarea { width: 100%; height: 200px; font-family: monospace; }
                    button { padding: 10px 20px; margin: 5px; }
                    .output { background: #f4f4f4; padding: 10px; margin: 10px 0; white-space: pre-wrap; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>Mobile Code Server</h1>
                    <p>Execute Python and Node.js code remotely on your Android device.</p>
                    
                    <label>Language:</label>
                    <select id="language">
                        <option value="python">Python</option>
                        <option value="nodejs">Node.js</option>
                    </select>
                    
                    <br><br>
                    <textarea id="code" placeholder="Enter your code here..."></textarea>
                    <br>
                    <button onclick="executeCode()">Execute</button>
                    <button onclick="getStatus()">Status</button>
                    <button onclick="getProcesses()">Processes</button>
                    
                    <div id="output" class="output"></div>
                </div>
                
                <script>
                    async function executeCode() {
                        const code = document.getElementById('code').value;
                        const language = document.getElementById('language').value;
                        
                        const response = await fetch('/execute', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ code, language })
                        });
                        
                        const result = await response.json();
                        document.getElementById('output').textContent = JSON.stringify(result, null, 2);
                    }
                    
                    async function getStatus() {
                        const response = await fetch('/status');
                        const result = await response.json();
                        document.getElementById('output').textContent = JSON.stringify(result, null, 2);
                    }
                    
                    async function getProcesses() {
                        const response = await fetch('/processes');
                        const result = await response.json();
                        document.getElementById('output').textContent = JSON.stringify(result, null, 2);
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
        
        sendResponse(output, 200, html, "text/html")
    }
    
    private fun handleStatus(output: java.io.OutputStream) {
        val status = JsonObject().apply {
            addProperty("server", "running")
            addProperty("port", getServerPort())
            addProperty("timestamp", System.currentTimeMillis())
        }
        sendJsonResponse(output, 200, status)
    }
    
    private fun handleExecute(output: java.io.OutputStream, body: String) {
        try {
            val request = gson.fromJson(body, JsonObject::class.java)
            val code = request.get("code")?.asString ?: ""
            val language = request.get("language")?.asString ?: "python"
            val processId = java.util.UUID.randomUUID().toString()
            
            if (code.isBlank()) {
                sendJsonResponse(output, 400, JsonObject().apply {
                    addProperty("error", "Code is required")
                })
                return
            }
            
            val result = when (language.lowercase()) {
                "python" -> codeExecutionService?.executePythonCode(code, processId)
                "nodejs", "node.js", "javascript" -> codeExecutionService?.executeNodeJsCode(code, processId)
                else -> CodeExecutionService.ExecutionResult.error("Unsupported language: $language")
            }
            
            val response = JsonObject().apply {
                addProperty("processId", processId)
                addProperty("language", language)
                addProperty("success", result?.isSuccess ?: false)
                addProperty("output", result?.output ?: "")
                addProperty("error", result?.error)
            }
            
            sendJsonResponse(output, 200, response)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error executing code: ${e.message}")
            sendJsonResponse(output, 500, JsonObject().apply {
                addProperty("error", "Internal server error: ${e.message}")
            })
        }
    }
    
    private fun handleProcesses(output: java.io.OutputStream) {
        val processes = codeExecutionService?.getRunningProcesses() ?: emptyList()
        val response = JsonObject().apply {
            add("processes", gson.toJsonTree(processes))
            addProperty("count", processes.size)
        }
        sendJsonResponse(output, 200, response)
    }
    
    private fun handleStopProcess(output: java.io.OutputStream, path: String) {
        val processId = path.substringAfterLast("/")
        val stopped = codeExecutionService?.stopProcess(processId) ?: false
        
        val response = JsonObject().apply {
            addProperty("processId", processId)
            addProperty("stopped", stopped)
        }
        sendJsonResponse(output, 200, response)
    }
    
    private fun handleOptions(output: java.io.OutputStream) {
        val headers = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Methods" to "GET, POST, DELETE, OPTIONS",
            "Access-Control-Allow-Headers" to "Content-Type"
        )
        sendResponse(output, 200, "", "text/plain", headers)
    }
    
    private fun sendJsonResponse(output: java.io.OutputStream, status: Int, json: JsonObject) {
        val headers = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Content-Type" to "application/json"
        )
        sendResponse(output, status, gson.toJson(json), "application/json", headers)
    }
    
    private fun sendResponse(
        output: java.io.OutputStream, 
        status: Int, 
        body: String, 
        contentType: String = "text/plain",
        additionalHeaders: Map<String, String> = emptyMap()
    ) {
        val statusText = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            500 -> "Internal Server Error"
            else -> "Unknown"
        }
        
        val response = StringBuilder()
        response.append("HTTP/1.1 $status $statusText\r\n")
        response.append("Content-Type: $contentType\r\n")
        response.append("Content-Length: ${body.toByteArray().size}\r\n")
        response.append("Connection: close\r\n")
        
        additionalHeaders.forEach { (key, value) ->
            response.append("$key: $value\r\n")
        }
        
        response.append("\r\n")
        response.append(body)
        
        output.write(response.toString().toByteArray())
        output.flush()
    }
}