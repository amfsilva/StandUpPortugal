package pt.standupportugal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.standupportugal.app.ui.StandUpFeedScreen
import pt.standupportugal.app.ui.StandUpPortugalTheme
import pt.standupportugal.app.ui.StandUpViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StandUpPortugalTheme {
                val vm: StandUpViewModel = viewModel()
                StandUpFeedScreen(viewModel = vm)
            }
        }
    }
}
