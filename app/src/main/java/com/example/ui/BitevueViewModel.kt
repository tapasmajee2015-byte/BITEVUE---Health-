package com.example.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DateKeyUtils
import com.example.data.FoodScanResult
import com.example.data.FoodScannerService
import com.example.data.MealCategory
import com.example.data.MealEntry
import com.example.data.MealSource
import com.example.data.NutritionRepository
import com.example.data.PresetMeal
import com.example.data.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "BitevueViewModel"

sealed interface StreamState<out T> {
    data object Loading : StreamState<Nothing>
    data class Success<T>(val data: T) : StreamState<T>
    data class Error(val message: String) : StreamState<Nothing>
}

sealed interface FoodScanUiState {
    data object Idle : FoodScanUiState
    data class Analyzing(val bitmap: Bitmap) : FoodScanUiState
    data class Success(val bitmap: Bitmap, val result: FoodScanResult) : FoodScanUiState
    data class Error(val bitmap: Bitmap?, val message: String) : FoodScanUiState
}

data class DailyNutritionSummary(
    val totalCalories: Int = 0,
    val totalProtein: Int = 0,
    val totalCarbs: Int = 0,
    val totalFat: Int = 0
)

data class BitevueUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val selectedDateKey: String = DateKeyUtils.todayKey(),
    val recentDateKeys: List<String> = DateKeyUtils.recentDateKeys(),
    val profile: UserProfile = UserProfile(),
    val mealsForSelectedDate: List<MealEntry> = emptyList(),
    val todayMeals: List<MealEntry> = emptyList(),
    val todaySummary: DailyNutritionSummary = DailyNutritionSummary(),
    val selectedDateSummary: DailyNutritionSummary = DailyNutritionSummary(),
    val todayWaterGlasses: Int = 0,
    val isSaving: Boolean = false
)

sealed interface SnackbarEvent {
    data class Info(val message: String) : SnackbarEvent
    data class MealDeleted(val meal: MealEntry) : SnackbarEvent
    data class Error(val message: String) : SnackbarEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class BitevueViewModel(
    private val repository: NutritionRepository,
    private val scannerService: FoodScannerService,
    private val currentUserId: String,
    initialDisplayName: String
) : ViewModel() {

    private val todayKey: String = DateKeyUtils.todayKey()
    private val _selectedDateKey = MutableStateFlow(todayKey)
    private val _isSaving = MutableStateFlow(false)

    private val _foodScanState = MutableStateFlow<FoodScanUiState>(FoodScanUiState.Idle)
    val foodScanState: StateFlow<FoodScanUiState> = _foodScanState.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<SnackbarEvent>(extraBufferCapacity = 4)
    val snackbarEvents: SharedFlow<SnackbarEvent> = _snackbarEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            val result = repository.ensureProfileAndSeedStarterData(
                userId = currentUserId,
                initialDisplayName = initialDisplayName,
                todayDateKey = todayKey
            )
            result.onFailure { err ->
                Log.w(TAG, "Profile initialization failed", err)
                _snackbarEvents.tryEmit(
                    SnackbarEvent.Error(err.localizedMessage ?: "Could not sync your account.")
                )
            }
        }
    }

    private val profileStream: StateFlow<StreamState<UserProfile>> = repository
        .observeUserProfile(currentUserId)
        .map<UserProfile?, StreamState<UserProfile>> { profile ->
            StreamState.Success(profile ?: UserProfile(userId = currentUserId, displayName = initialDisplayName))
        }
        .catch { error ->
            val firestoreError = unwrapFirestoreError(error)
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing profile", root)
            emit(StreamState.Error(root.localizedMessage ?: "Failed to load profile"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = StreamState.Loading
        )

    private val todayMealsStream: StateFlow<StreamState<List<MealEntry>>> = repository
        .observeMealsForDate(currentUserId, todayKey)
        .map<List<MealEntry>, StreamState<List<MealEntry>>> { StreamState.Success(it) }
        .catch { error ->
            val firestoreError = unwrapFirestoreError(error)
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing today's meals", root)
            emit(StreamState.Error(root.localizedMessage ?: "Failed to load today's meals"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = StreamState.Loading
        )

    private val selectedDateMealsStream: StateFlow<StreamState<List<MealEntry>>> = _selectedDateKey
        .flatMapLatest { dateKey ->
            repository.observeMealsForDate(currentUserId, dateKey)
                .map<List<MealEntry>, StreamState<List<MealEntry>>> { StreamState.Success(it) }
                .catch { error ->
                    val firestoreError = unwrapFirestoreError(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    val root = firestoreError ?: error
                    Log.w(TAG, "Error observing meals for $dateKey", root)
                    emit(StreamState.Error(root.localizedMessage ?: "Failed to load meal diary"))
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = StreamState.Loading
        )

    private val todayWaterStream: StateFlow<StreamState<Int>> = repository
        .observeHydrationForDate(currentUserId, todayKey)
        .map<Int, StreamState<Int>> { StreamState.Success(it) }
        .catch { error ->
            val firestoreError = unwrapFirestoreError(error)
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing hydration", root)
            emit(StreamState.Error(root.localizedMessage ?: "Failed to load hydration"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = StreamState.Loading
        )

    val uiState: StateFlow<BitevueUiState> = combine(
        profileStream,
        todayMealsStream,
        selectedDateMealsStream,
        todayWaterStream,
        combine(_selectedDateKey, _isSaving) { dateKey, saving -> dateKey to saving }
    ) { profileState, todayMealsState, selectedMealsState, waterState, (selectedDate, saving) ->
        val firstError = listOf(profileState, todayMealsState, selectedMealsState, waterState)
            .filterIsInstance<StreamState.Error>()
            .firstOrNull()?.message

        val isLoading = profileState is StreamState.Loading && todayMealsState is StreamState.Loading

        val profile = (profileState as? StreamState.Success)?.data ?: UserProfile(userId = currentUserId)
        val todayMeals = (todayMealsState as? StreamState.Success)?.data.orEmpty()
        val selectedMeals = (selectedMealsState as? StreamState.Success)?.data.orEmpty()
        val water = (waterState as? StreamState.Success)?.data ?: 0

        BitevueUiState(
            isLoading = isLoading,
            errorMessage = firstError,
            selectedDateKey = selectedDate,
            recentDateKeys = DateKeyUtils.recentDateKeys(),
            profile = profile,
            mealsForSelectedDate = selectedMeals,
            todayMeals = todayMeals,
            todaySummary = summarize(todayMeals),
            selectedDateSummary = summarize(selectedMeals),
            todayWaterGlasses = water,
            isSaving = saving
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = BitevueUiState()
    )

    fun selectDate(dateKey: String) {
        _selectedDateKey.value = dateKey
    }

    fun addWaterGlass() {
        val current = uiState.value.todayWaterGlasses
        val goal = uiState.value.profile.waterGoal.toInt().coerceIn(1, 16)
        val next = (current + 1).coerceAtMost(maxOf(goal, 16))
        setWaterGlasses(next)
    }

    fun setWaterGlasses(targetGlasses: Int) {
        viewModelScope.launch {
            val result = repository.setHydration(
                userId = currentUserId,
                dateKey = todayKey,
                glasses = targetGlasses.coerceIn(0, 16)
            )
            result.onFailure { err ->
                _snackbarEvents.tryEmit(
                    SnackbarEvent.Error(err.localizedMessage ?: "Could not update hydration.")
                )
            }
        }
    }

    fun logPresetMeal(preset: PresetMeal, dateKey: String = _selectedDateKey.value) {
        logMeal(
            dateKey = dateKey,
            category = preset.category,
            name = preset.name,
            detail = preset.detail,
            calories = preset.calories,
            proteinGrams = preset.proteinGrams,
            carbsGrams = preset.carbsGrams,
            fatGrams = preset.fatGrams,
            source = MealSource.PRESET,
            insight = null
        )
    }

    fun logMeal(
        dateKey: String = _selectedDateKey.value,
        category: MealCategory,
        name: String,
        detail: String,
        calories: Int,
        proteinGrams: Int,
        carbsGrams: Int,
        fatGrams: Int,
        source: MealSource,
        insight: String? = null,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.addMeal(
                userId = currentUserId,
                dateKey = dateKey,
                category = category,
                name = name,
                detail = detail,
                calories = calories,
                proteinGrams = proteinGrams,
                carbsGrams = carbsGrams,
                fatGrams = fatGrams,
                source = source,
                insight = insight
            )
            _isSaving.value = false
            result.onSuccess {
                _snackbarEvents.tryEmit(SnackbarEvent.Info("Added \"$name\" to your diary"))
                onSuccess()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(
                    SnackbarEvent.Error(err.localizedMessage ?: "Failed to save meal.")
                )
            }
        }
    }

    fun deleteMeal(meal: MealEntry) {
        if (meal.id.isBlank()) return
        viewModelScope.launch {
            val result = repository.deleteMeal(currentUserId, meal.id)
            result.onSuccess {
                _snackbarEvents.tryEmit(SnackbarEvent.MealDeleted(meal))
            }.onFailure { err ->
                _snackbarEvents.tryEmit(
                    SnackbarEvent.Error(err.localizedMessage ?: "Failed to remove meal.")
                )
            }
        }
    }

    fun restoreDeletedMeal(meal: MealEntry) {
        logMeal(
            dateKey = meal.dateKey.ifBlank { todayKey },
            category = meal.mealCategory,
            name = meal.name,
            detail = meal.detail,
            calories = meal.calories.toInt(),
            proteinGrams = meal.proteinGrams.toInt(),
            carbsGrams = meal.carbsGrams.toInt(),
            fatGrams = meal.fatGrams.toInt(),
            source = meal.mealSource,
            insight = meal.insight
        )
    }

    fun saveGoalsAndProfile(
        displayName: String,
        calorieGoal: Int,
        proteinGoal: Int,
        carbsGoal: Int,
        fatGoal: Int,
        waterGoal: Int
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.updateProfileAndGoals(
                userId = currentUserId,
                displayName = displayName,
                calorieGoal = calorieGoal,
                proteinGoal = proteinGoal,
                carbsGoal = carbsGoal,
                fatGoal = fatGoal,
                waterGoal = waterGoal
            )
            _isSaving.value = false
            result.onSuccess {
                _snackbarEvents.tryEmit(SnackbarEvent.Info("Daily nutrition goals saved"))
            }.onFailure { err ->
                _snackbarEvents.tryEmit(
                    SnackbarEvent.Error(err.localizedMessage ?: "Failed to update goals.")
                )
            }
        }
    }

    fun analyzeFoodBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            _foodScanState.value = FoodScanUiState.Analyzing(bitmap)
            val result = scannerService.analyzeFoodPhoto(bitmap)
            result.onSuccess { parsed ->
                _foodScanState.value = FoodScanUiState.Success(bitmap, parsed)
            }.onFailure { err ->
                _foodScanState.value = FoodScanUiState.Error(
                    bitmap = bitmap,
                    message = err.localizedMessage ?: "Unable to analyze food image."
                )
            }
        }
    }

    fun resetScannerState() {
        _foodScanState.value = FoodScanUiState.Idle
    }

    private fun summarize(meals: List<MealEntry>): DailyNutritionSummary =
        DailyNutritionSummary(
            totalCalories = meals.sumOf { it.calories.toInt() },
            totalProtein = meals.sumOf { it.proteinGrams.toInt() },
            totalCarbs = meals.sumOf { it.carbsGrams.toInt() },
            totalFat = meals.sumOf { it.fatGrams.toInt() }
        )

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
    }
}
