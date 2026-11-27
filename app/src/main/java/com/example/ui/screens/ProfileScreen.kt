package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.BitevueUiState
import com.example.ui.theme.BitevueThemeTokens
import com.example.ui.theme.FrauncesFontFamily

@Composable
fun ProfileScreen(
    uiState: BitevueUiState,
    userEmail: String?,
    onSaveGoals: (
        displayName: String,
        calorieGoal: Int,
        proteinGoal: Int,
        carbsGoal: Int,
        fatGoal: Int,
        waterGoal: Int
    ) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = uiState.profile
    val extended = BitevueThemeTokens.extended

    var displayName by remember(profile.displayName) { mutableStateOf(profile.displayName) }
    var calorieText by remember(profile.calorieGoal) { mutableStateOf(profile.calorieGoal.toString()) }
    var proteinText by remember(profile.proteinGoal) { mutableStateOf(profile.proteinGoal.toString()) }
    var carbsText by remember(profile.carbsGoal) { mutableStateOf(profile.carbsGoal.toString()) }
    var fatText by remember(profile.fatGoal) { mutableStateOf(profile.fatGoal.toString()) }
    var waterText by remember(profile.waterGoal) { mutableStateOf(profile.waterGoal.toString()) }
    var validationError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(profile) {
        displayName = profile.displayName
        calorieText = profile.calorieGoal.toString()
        proteinText = profile.proteinGoal.toString()
        carbsText = profile.carbsGoal.toString()
        fatText = profile.fatGoal.toString()
        waterText = profile.waterGoal.toString()
    }

    val initialBadge = displayName.trim().firstOrNull()?.lowercaseChar()?.let { "$it." } ?: "b."

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "profile_header") {
            Column {
                Text(
                    text = stringResource(R.string.eyebrow_account),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.profile_title),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Welcome & Cloud Account Card
        item(key = "account_card") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initialBadge,
                            fontFamily = FrauncesFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 25.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (!userEmail.isNullOrBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.CloudDone,
                                contentDescription = null,
                                tint = extended.success,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "Synced with Google & Gemini AI ready",
                                style = MaterialTheme.typography.bodySmall,
                                color = extended.success
                            )
                        }
                    }
                }
            }
        }

        // Editable Goals Form
        item(key = "goals_card") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daily nutrition goals",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        TextButton(
                            onClick = {
                                validationError = null
                                calorieText = "2000"
                                proteinText = "120"
                                carbsText = "220"
                                fatText = "65"
                                waterText = "8"
                            },
                            modifier = Modifier.testTag("reset_goals_button")
                        ) {
                            Text("Reset", color = MaterialTheme.colorScheme.secondary)
                        }
                    }

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it.take(60) },
                        label = { Text("Display name") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_name_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumericGoalField(
                            label = "Calories (kcal)",
                            value = calorieText,
                            onValueChange = { calorieText = it.filter(Char::isDigit).take(4) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("goal_calories_input")
                        )
                        NumericGoalField(
                            label = "Water (glasses)",
                            value = waterText,
                            onValueChange = { waterText = it.filter(Char::isDigit).take(2) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("goal_water_input")
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumericGoalField(
                            label = "Protein (g)",
                            value = proteinText,
                            onValueChange = { proteinText = it.filter(Char::isDigit).take(3) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("goal_protein_input")
                        )
                        NumericGoalField(
                            label = "Carbs (g)",
                            value = carbsText,
                            onValueChange = { carbsText = it.filter(Char::isDigit).take(3) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("goal_carbs_input")
                        )
                        NumericGoalField(
                            label = "Fat (g)",
                            value = fatText,
                            onValueChange = { fatText = it.filter(Char::isDigit).take(3) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("goal_fat_input")
                        )
                    }

                    if (validationError != null) {
                        Text(
                            text = validationError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    Button(
                        onClick = {
                            val name = displayName.trim()
                            val cal = calorieText.toIntOrNull()
                            val pro = proteinText.toIntOrNull()
                            val carb = carbsText.toIntOrNull()
                            val fat = fatText.toIntOrNull()
                            val water = waterText.toIntOrNull()
                            when {
                                name.isEmpty() -> validationError = "Please enter a display name."
                                cal == null || cal !in 800..6000 ->
                                    validationError = "Calories must be between 800 and 6,000 kcal."
                                pro == null || pro !in 10..400 ->
                                    validationError = "Protein must be between 10 and 400 g."
                                carb == null || carb !in 10..800 ->
                                    validationError = "Carbs must be between 10 and 800 g."
                                fat == null || fat !in 10..300 ->
                                    validationError = "Fat must be between 10 and 300 g."
                                water == null || water !in 1..16 ->
                                    validationError = "Water goal must be between 1 and 16 glasses."
                                else -> {
                                    validationError = null
                                    onSaveGoals(name, cal, pro, carb, fat, water)
                                }
                            }
                        },
                        enabled = !uiState.isSaving,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("save_goals_button")
                    ) {
                        Text(
                            text = if (uiState.isSaving) "Saving..." else "Save daily goals",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }

        // Sign Out Action
        item(key = "sign_out_card") {
            OutlinedButton(
                onClick = onSignOut,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.sign_out),
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}

@Composable
private fun NumericGoalField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    )
}
