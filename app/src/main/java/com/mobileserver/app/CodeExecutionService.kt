package com.mobileserver.app

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import io.liquidcore.service.LiquidService
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future

class CodeExecutionService : Service() {
    
    private val binder = LocalBinder()
    private val executor = Executors.newCachedThreadPool()
    private val runningProcesses = ConcurrentHashMap<String, Future<*>>()
    private lateinit var python: Python
    
    companion object {
        private const val TAG = "CodeExecutionService"
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): CodeExecutionService = this@CodeExecutionService
    }
    
    override fun onCreate() {
        super.onCreate()
        initializePython()
        Log.d(TAG, "CodeExecutionService created")
    }
    
    private fun initializePython() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        python = Python.getInstance()
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    fun executePythonCode(code: String, processId: String): ExecutionResult {
        return try {
            val future = executor.submit<ExecutionResult> {
                try {
                    val pyObject = python.getModule("builtins")
                    val result = pyObject.callAttr("exec", code)
                    ExecutionResult.success(result?.toString() ?: "Code executed successfully")
                } catch (e: Exception) {
                    ExecutionResult.error("Python execution error: ${e.message}")
                }
            }
            
            runningProcesses[processId] = future
            val result = future.get()
            runningProcesses.remove(processId)
            result
        } catch (e: Exception) {
            ExecutionResult.error("Failed to execute Python code: ${e.message}")
        }
    }
    
    fun executeNodeJsCode(code: String, processId: String): ExecutionResult {
        return try {
            val future = executor.submit<ExecutionResult> {
                try {
                    val service = LiquidService(this@CodeExecutionService, 
                        LiquidService.Options().enableConsole(true))
                    
                    var result = ""
                    var error = ""
                    
                    service.addEventListener("message") { event ->
                        val data = event.data as? JSONObject
                        result = data?.optString("result", "") ?: ""
                        error = data?.optString("error", "") ?: ""
                    }
                    
                    // Wrap user code to capture output
                    val wrappedCode = """
                        try {
                            const result = (function() {
                                $code
                            })();
                            LiquidCore.emit('message', {result: String(result)});
                        } catch (e) {
                            LiquidCore.emit('message', {error: e.message});
                        }
                    """.trimIndent()
                    
                    service.start(wrappedCode)
                    
                    // Wait for execution
                    Thread.sleep(1000)
                    
                    if (error.isNotEmpty()) {
                        ExecutionResult.error("Node.js execution error: $error")
                    } else {
                        ExecutionResult.success(result.ifEmpty { "Code executed successfully" })
                    }
                } catch (e: Exception) {
                    ExecutionResult.error("Node.js execution error: ${e.message}")
                }
            }
            
            runningProcesses[processId] = future
            val result = future.get()
            runningProcesses.remove(processId)
            result
        } catch (e: Exception) {
            ExecutionResult.error("Failed to execute Node.js code: ${e.message}")
        }
    }
    
    fun stopProcess(processId: String): Boolean {
        val future = runningProcesses[processId]
        return if (future != null) {
            future.cancel(true)
            runningProcesses.remove(processId)
            true
        } else {
            false
        }
    }
    
    fun getRunningProcesses(): List<String> {
        return runningProcesses.keys.toList()
    }
    
    data class ExecutionResult(
        val isSuccess: Boolean,
        val output: String,
        val error: String? = null
    ) {
        companion object {
            fun success(output: String) = ExecutionResult(true, output)
            fun error(error: String) = ExecutionResult(false, "", error)
        }
    }
}