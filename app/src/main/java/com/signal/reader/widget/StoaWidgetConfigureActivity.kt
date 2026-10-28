package com.signal.reader.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.signal.reader.SignalApplication
import com.signal.reader.ui.settings.WidgetSettingsScreen
import com.signal.reader.ui.theme.SignalTheme

class StoaWidgetConfigureActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Default to CANCELED per Android AppWidget spec
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        val app = application as SignalApplication

        setContent {
            val settings by app.settingsManager.settingsFlow.collectAsState()

            SignalTheme(themePreference = settings.theme) {
                WidgetSettingsScreen(
                    settingsManager = app.settingsManager,
                    repository = app.repository,
                    onBack = {
                        completeAndFinish()
                    }
                )
            }
        }
    }

    private fun completeAndFinish() {
        StoaWidgetProvider.updateAllWidgets(this)

        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(Activity.RESULT_OK, resultValue)
        finish()
    }
}
