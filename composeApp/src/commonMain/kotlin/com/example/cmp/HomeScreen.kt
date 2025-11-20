@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.example.cmp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.cmp.games.CoolGeometricSilk

@Composable
fun HomeScreen(
    navigateToProduct: (Product) -> Unit
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Welcome to Nav3")

            CoolGeometricSilk(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            Button(onClick = {
                // To navigate to a new route, just add that route to the back stack
                navigateToProduct(Product("123"))
            }) {
                Text("Next Page")
            }
        }
    }
}