package com.mobileserver.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import java.util.*

class CodeEditorFragment : Fragment() {

    private lateinit var codeEditText: EditText
    private lateinit var languageSpinner: Spinner
    private lateinit var executeButton: Button
    private lateinit var outputTextView: TextView
    private lateinit var processListView: ListView
    
    private var codeExecutionService: CodeExecutionService? = null
    private var serviceBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as CodeExecutionService.LocalBinder
            codeExecutionService = binder.getService()
            serviceBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            serviceBound = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_code_editor, container, false)
        
        codeEditText = view.findViewById(R.id.codeEditText)
        languageSpinner = view.findViewById(R.id.languageSpinner)
        executeButton = view.findViewById(R.id.executeButton)
        outputTextView = view.findViewById(R.id.outputTextView)
        processListView = view.findViewById(R.id.processListView)
        
        setupLanguageSpinner()
        setupExecuteButton()
        
        return view
    }
    
    override fun onStart() {
        super.onStart()
        Intent(requireContext(), CodeExecutionService::class.java).also { intent ->
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
    
    private fun setupLanguageSpinner() {
        val languages = arrayOf("Python", "Node.js")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = adapter
    }
    
    private fun setupExecuteButton() {
        executeButton.setOnClickListener {
            val code = codeEditText.text.toString()
            val language = languageSpinner.selectedItem.toString()
            val processId = UUID.randomUUID().toString()
            
            if (code.isBlank()) {
                Toast.makeText(requireContext(), "Please enter some code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            executeCode(code, language, processId)
        }
    }
    
    private fun executeCode(code: String, language: String, processId: String) {
        outputTextView.text = "Executing..."
        
        Thread {
            val result = when (language) {
                "Python" -> codeExecutionService?.executePythonCode(code, processId)
                "Node.js" -> codeExecutionService?.executeNodeJsCode(code, processId)
                else -> CodeExecutionService.ExecutionResult.error("Unsupported language")
            }
            
            requireActivity().runOnUiThread {
                if (result != null) {
                    if (result.isSuccess) {
                        outputTextView.text = "Output:\n${result.output}"
                    } else {
                        outputTextView.text = "Error:\n${result.error}"
                    }
                } else {
                    outputTextView.text = "Service not available"
                }
                updateProcessList()
            }
        }.start()
    }
    
    private fun updateProcessList() {
        val processes = codeExecutionService?.getRunningProcesses() ?: emptyList()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, processes)
        processListView.adapter = adapter
        
        processListView.setOnItemClickListener { _, _, position, _ ->
            val processId = processes[position]
            codeExecutionService?.stopProcess(processId)
            Toast.makeText(requireContext(), "Process stopped", Toast.LENGTH_SHORT).show()
            updateProcessList()
        }
    }
}