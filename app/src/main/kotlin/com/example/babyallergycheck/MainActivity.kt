package com.example.babyallergycheck

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.babyallergycheck.ui.BabyAllergyTheme
import com.example.babyallergycheck.ui.BabyApp
import com.example.babyallergycheck.ui.BabyViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BabyAllergyTheme {
                val viewModel: BabyViewModel = viewModel()
                BabyApp(viewModel)
            }
        }
    }
}
