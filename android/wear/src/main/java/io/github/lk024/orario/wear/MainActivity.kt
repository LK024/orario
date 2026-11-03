package io.github.lk024.orario.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val loaded = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val app = application as WearApplication
        splash.setKeepOnScreenCondition { !loaded.value }
        lifecycleScope.launch {
            app.load()
            loaded.value = true
        }

        setContent {
            val schedule by app.scheduleRepository.schedule.collectAsStateWithLifecycle()
            val overrides by app.overridesRepository.overrides.collectAsStateWithLifecycle()
            // All'apertura aggiorna in background se i dati sono vecchi: la schermata non aspetta
            LaunchedEffect(Unit) { app.refresh() }
            OrarioWearTheme {
                WearApp(schedule = schedule, overrides = overrides)
            }
        }
    }
}
