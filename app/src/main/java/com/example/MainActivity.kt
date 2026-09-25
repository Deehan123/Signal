package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.ui.SignalApp
import com.example.ui.SignalViewModel
import com.example.ui.SignalViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: SignalViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize the offline radio system architecture
        val factory = SignalViewModelFactory(application)
        viewModel = ViewModelProvider(this, factory)[SignalViewModel::class.java]

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                SignalApp(viewModel = viewModel)
            }
        }
    }
}
