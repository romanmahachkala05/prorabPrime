package ru.prorabprime

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import ru.prorabprime.shared.App

class MainActivity : ComponentActivity() {

    // The answer needs no handling: without the permission requests fail and the screens say
    // the server does not answer, which is true from the app's side.
    private val localNetworkPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestLocalNetworkAccess()
        setContent { App() }
    }

    /** Android 17 put the local network behind a runtime permission; the server lives there (ADR-0009). */
    private fun requestLocalNetworkAccess() {
        if (Build.VERSION.SDK_INT < ANDROID_17) return
        if (checkSelfPermission(LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED) return
        localNetworkPermission.launch(LOCAL_NETWORK)
    }

    private companion object {
        const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
        const val ANDROID_17 = 37
    }
}
