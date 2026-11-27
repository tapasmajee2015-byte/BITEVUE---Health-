package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.FoodScannerService
import com.example.data.MealSource
import com.example.data.NutritionRepository
import com.example.firebase.configureAppCheck
import com.example.ui.BitevueViewModel
import com.example.ui.SnackbarEvent
import com.example.ui.components.FoodScannerSheet
import com.example.ui.components.MealLoggerSheet
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DiaryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.theme.BitevueTheme
import com.example.ui.theme.FrauncesFontFamily
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private enum class BitevueTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    HOME(R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home, "nav_tab_home"),
    DIARY(R.string.nav_diary, Icons.Filled.Book, Icons.Outlined.Book, "nav_tab_diary"),
    PROFILE(R.string.nav_profile, Icons.Filled.Person, Icons.Outlined.Person, "nav_tab_profile")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        // STEP 2: Enable edge-to-edge with light status bar icons for warm oat paper background
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        setContent {
            BitevueTheme {
                BitevueApp()
            }
        }
    }
}

@Composable
private fun BitevueApp(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { /* authStateFlow automatically transitions UI */ })
    } else {
        key(user.uid) {
            BitevueAuthenticatedShell(user = user)
        }
    }
}

@Composable
private fun BitevueAuthenticatedShell(user: FirebaseUser) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: BitevueViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                BitevueViewModel(
                    repository = NutritionRepository(db),
                    scannerService = FoodScannerService(),
                    currentUserId = user.uid,
                    initialDisplayName = user.displayName ?: user.email?.substringBefore("@") ?: "Bitevue Member"
                )
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scanState by viewModel.foodScanState.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(BitevueTab.HOME) }
    var showMealLogger by rememberSaveable { mutableStateOf(false) }
    var showFoodScanner by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.snackbarEvents.collect { event ->
            when (event) {
                is SnackbarEvent.Info -> snackbarHostState.showSnackbar(event.message)
                is SnackbarEvent.Error -> snackbarHostState.showSnackbar(event.message)
                is SnackbarEvent.MealDeleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Removed \"${event.meal.name}\"",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.restoreDeletedMeal(event.meal)
                    }
                }
            }
        }
    }

    BackHandler(enabled = currentTab != BitevueTab.HOME) {
        currentTab = BitevueTab.HOME
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.navigationBarsPadding()
                )
            },
            floatingActionButton = {
                if (currentTab == BitevueTab.DIARY) {
                    ExtendedFloatingActionButton(
                        onClick = { showMealLogger = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .then(if (useNavRail) Modifier.navigationBarsPadding() else Modifier)
                            .testTag("diary_add_meal_fab")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.log_meal),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            },
            bottomBar = {
                if (!useNavRail) {
                    BitevueFloatingBottomBar(
                        currentTab = currentTab,
                        onSelectTab = { currentTab = it }
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                    ) {
                        Spacer(Modifier.size(16.dp))
                        BitevueTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.secondary,
                                    selectedTextColor = MaterialTheme.colorScheme.onBackground,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .statusBarsPadding(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxSize()
                            .padding(horizontal = 22.dp)
                    ) {
                        BrandTopBar()

                        Box(modifier = Modifier.weight(1f)) {
                            when (currentTab) {
                                BitevueTab.HOME -> HomeScreen(
                                    uiState = uiState,
                                    onOpenScanner = { showFoodScanner = true },
                                    onOpenMealLogger = { showMealLogger = true },
                                    onAddWater = { viewModel.addWaterGlass() },
                                    onSetWater = { viewModel.setWaterGlasses(it) },
                                    onOpenProfile = { currentTab = BitevueTab.PROFILE }
                                )

                                BitevueTab.DIARY -> DiaryScreen(
                                    uiState = uiState,
                                    onSelectDate = { viewModel.selectDate(it) },
                                    onDeleteMeal = { viewModel.deleteMeal(it) },
                                    onOpenMealLogger = { showMealLogger = true },
                                    onOpenScanner = { showFoodScanner = true }
                                )

                                BitevueTab.PROFILE -> ProfileScreen(
                                    uiState = uiState,
                                    userEmail = user.email,
                                    onSaveGoals = { name, cal, pro, carb, fat, water ->
                                        viewModel.saveGoalsAndProfile(name, cal, pro, carb, fat, water)
                                    },
                                    onSignOut = {
                                        signOutUser(credentialManager, scope)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMealLogger) {
        MealLoggerSheet(
            isSaving = uiState.isSaving,
            onDismiss = { showMealLogger = false },
            onSelectPreset = { preset ->
                viewModel.logPresetMeal(preset)
                showMealLogger = false
            },
            onSubmitCustomMeal = { category, name, detail, calories, protein, carbs, fat ->
                viewModel.logMeal(
                    category = category,
                    name = name,
                    detail = detail,
                    calories = calories,
                    proteinGrams = protein,
                    carbsGrams = carbs,
                    fatGrams = fat,
                    source = MealSource.MANUAL,
                    onSuccess = { showMealLogger = false }
                )
            }
        )
    }

    if (showFoodScanner) {
        FoodScannerSheet(
            scanState = scanState,
            isSaving = uiState.isSaving,
            onDismiss = {
                showFoodScanner = false
                viewModel.resetScannerState()
            },
            onAnalyzeBitmap = { bitmap -> viewModel.analyzeFoodBitmap(bitmap) },
            onReset = { viewModel.resetScannerState() },
            onSaveScannedMeal = { category, name, detail, calories, protein, carbs, fat, insight ->
                viewModel.logMeal(
                    category = category,
                    name = name,
                    detail = detail,
                    calories = calories,
                    proteinGrams = protein,
                    carbsGrams = carbs,
                    fatGrams = fat,
                    source = MealSource.AI_SCAN,
                    insight = insight,
                    onSuccess = {
                        showFoodScanner = false
                        viewModel.resetScannerState()
                    }
                )
            }
        )
    }
}

@Composable
private fun BrandTopBar() {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 18.dp)
    ) {
        Text(
            text = buildAnnotatedString {
                append(stringResource(R.string.brand_name))
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.secondary)) {
                    append(".")
                }
            },
            fontFamily = FrauncesFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 27.sp,
            letterSpacing = (-1.0).sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = stringResource(R.string.brand_tagline),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 3.dp)
        )
    }
}

@Composable
private fun BitevueFloatingBottomBar(
    currentTab: BitevueTab,
    onSelectTab: (BitevueTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BitevueTab.entries.forEach { tab ->
                    val selected = tab == currentTab
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                } else {
                                    Color.Transparent
                                }
                            )
                            .clickable(role = Role.Tab) { onSelectTab(tab) }
                            .padding(vertical = 8.dp)
                            .testTag(tab.tag)
                    ) {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = stringResource(tab.labelRes),
                            tint = if (selected) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 0.2.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onBackground
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}
