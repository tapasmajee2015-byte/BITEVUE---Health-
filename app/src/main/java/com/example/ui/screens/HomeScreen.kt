package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.BitevueUiState
import com.example.ui.theme.BitevueThemeTokens
import com.example.ui.theme.FrauncesFontFamily
import java.text.NumberFormat
import java.util.Locale

@Composable
fun HomeScreen(
    uiState: BitevueUiState,
    onOpenScanner: () -> Unit,
    onOpenMealLogger: () -> Unit,
    onAddWater: () -> Unit,
    onSetWater: (Int) -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extended = BitevueThemeTokens.extended
    val numberFmt = NumberFormat.getIntegerInstance(Locale.getDefault())
    val profile = uiState.profile
    val summary = uiState.todaySummary

    val calorieGoal = profile.calorieGoal.toInt().coerceAtLeast(1)
    val calorieRatio = (summary.totalCalories.toFloat() / calorieGoal.toFloat()).coerceIn(0f, 1f)
    val animatedCalorieProgress by animateFloatAsState(
        targetValue = calorieRatio,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "calorie_progress"
    )
    val remainingCalories = calorieGoal - summary.totalCalories

    val initialBadge = profile.displayName.trim().firstOrNull()?.lowercaseChar()?.let { "$it." } ?: "b."

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        if (uiState.errorMessage != null) {
            item(key = "error_banner") {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }

        // Greeting header
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.eyebrow_daily_wellness),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${stringResource(R.string.hero_heading_line_1)}\n${stringResource(R.string.hero_heading_line_2)}",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(role = Role.Button, onClick = onOpenProfile)
                        .testTag("home_avatar_badge"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initialBadge,
                        fontFamily = FrauncesFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 23.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        // Daily Calories Hero Card
        item(key = "calorie_hero_card") {
            val glowColor = extended.heroFill.copy(alpha = 0.14f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(extended.heroCardBg)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(glowColor, Color.Transparent),
                                center = Offset(size.width * 0.92f, size.height * 0.15f),
                                radius = size.width * 0.55f
                            ),
                            radius = size.width * 0.55f,
                            center = Offset(size.width * 0.92f, size.height * 0.15f)
                        )
                    }
                    .padding(22.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.card_daily_calories),
                            style = MaterialTheme.typography.labelSmall,
                            color = extended.heroEyebrow
                        )
                        Surface(
                            color = extended.heroTrack,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(role = Role.Button, onClick = onOpenMealLogger)
                                .testTag("home_quick_log_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Log a meal",
                                    tint = extended.heroFill,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Log meal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    letterSpacing = 0.2.sp
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = numberFmt.format(summary.totalCalories),
                            fontFamily = FrauncesFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 40.sp,
                            lineHeight = 44.sp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "/ ${numberFmt.format(calorieGoal)} kcal",
                            style = MaterialTheme.typography.bodyMedium,
                            color = extended.heroEyebrow,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(extended.heroTrack)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedCalorieProgress)
                                .height(8.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(extended.heroFill)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    val hintText = when {
                        remainingCalories > 0 ->
                            "${numberFmt.format(remainingCalories)} kcal remaining in your daily goal"
                        remainingCalories == 0 ->
                            "Right on target for your daily calorie goal"
                        else ->
                            "${numberFmt.format(-remainingCalories)} kcal above your daily target"
                    }
                    Text(
                        text = hintText,
                        style = MaterialTheme.typography.bodySmall,
                        color = extended.heroHint
                    )
                }
            }
        }

        // Your nutrition section
        item(key = "nutrition_card") {
            Column {
                Text(
                    text = stringResource(R.string.section_your_nutrition),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        MacroProgressRow(
                            label = "Protein",
                            value = summary.totalProtein,
                            goal = profile.proteinGoal.toInt().coerceAtLeast(1),
                            unit = "g",
                            barColor = extended.proteinAccent,
                            trackColor = extended.macroTrack
                        )
                        MacroProgressRow(
                            label = "Carbs",
                            value = summary.totalCarbs,
                            goal = profile.carbsGoal.toInt().coerceAtLeast(1),
                            unit = "g",
                            barColor = extended.carbsAccent,
                            trackColor = extended.macroTrack
                        )
                        MacroProgressRow(
                            label = "Fat",
                            value = summary.totalFat,
                            goal = profile.fatGoal.toInt().coerceAtLeast(1),
                            unit = "g",
                            barColor = extended.fatAccent,
                            trackColor = extended.macroTrack
                        )
                    }
                }
            }
        }

        // Quick actions — Scan your food
        item(key = "scan_action_card") {
            Column {
                Text(
                    text = stringResource(R.string.section_quick_actions),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(role = Role.Button, onClick = onOpenScanner)
                        .testTag("scan_food_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CenterFocusWeak,
                                contentDescription = "AI Food Scanner",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.scan_food_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = stringResource(R.string.scan_food_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        // Today's hydration
        item(key = "hydration_card") {
            val waterGoal = profile.waterGoal.toInt().coerceIn(1, 16)
            val displaySegments = waterGoal.coerceAtMost(12)
            Column {
                Text(
                    text = stringResource(R.string.section_todays_hydration),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(extended.waterEmpty),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.WaterDrop,
                                        contentDescription = null,
                                        tint = extended.waterFilled,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.water_intake_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Text(
                                text = "${uiState.todayWaterGlasses} / $waterGoal glasses",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (index in 0 until displaySegments) {
                                val filled = index < uiState.todayWaterGlasses
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (filled) extended.waterFilled else extended.waterEmpty)
                                        .clickable(role = Role.Button) {
                                            val target = if (uiState.todayWaterGlasses == index + 1) {
                                                index
                                            } else {
                                                index + 1
                                            }
                                            onSetWater(target)
                                        }
                                        .testTag("water_segment_$index")
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(role = Role.Button, onClick = onAddWater)
                                .testTag("add_water_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 13.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.add_water_button),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroProgressRow(
    label: String,
    value: Int,
    goal: Int,
    unit: String,
    barColor: Color,
    trackColor: Color
) {
    val ratio = (value.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = ratio,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "macro_$label"
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "$value$unit / $goal$unit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(9.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(trackColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(barColor)
            )
        }
    }
}
