package com.mobileserver.app

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.eclipsesource.v8.V8
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
                var v8: V8? = null
                try {
                    v8 = V8.createV8Runtime()
                    
                    // Execute the JavaScript code
                    val result = v8.executeScript(code)
                    
                    // Convert result to string
                    val output = when {
                        result == null -> "null"
                        result is String -> result
                        else -> result.toString()
                    }
                    
                    ExecutionResult.success(output)
                } catch (e: Exception) {
                    ExecutionResult.error("JavaScript execution error: ${e.message}")
                } finally {
                    v8?.release()
                }
            }
            
            runningProcesses[processId] = future
            val result = future.get()
            runningProcesses.remove(processId)
            result
        } catch (e: Exception) {
            ExecutionResult.error("Failed to execute JavaScript code: ${e.message}")
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