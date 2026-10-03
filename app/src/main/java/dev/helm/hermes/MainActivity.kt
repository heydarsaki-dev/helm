package dev.helm.hermes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {

    private val vm: HelmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { HelmApp(vm) }
    }

    override fun onResume() {
        super.onResume()
        // The gateway is a process someone else started in a terminal. Coming
        // back to the app is the natural moment to find out whether it is still
        // there, and cheap to ask.
        vm.probe(announce = false)
    }
}
