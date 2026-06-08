package com.smartleaf.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.smartleaf.app.ui.MainViewModel
import com.smartleaf.app.ui.navigation.SmartLeafNavGraph
import com.smartleaf.app.ui.theme.SmartLeafTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartLeafTheme {
                SmartLeafNavGraph(viewModel = viewModel)
            }
        }
    }
}
