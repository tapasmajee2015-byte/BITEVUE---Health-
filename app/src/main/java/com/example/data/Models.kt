package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class MealCategory(val displayName: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snack");

    companion object {
        fun fromRaw(raw: String?): MealCategory =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SNACK
    }
}

enum class MealSource(val label: String) {
    MANUAL("Logged manually"),
    PRESET("Quick preset"),
    AI_SCAN("AI scanned");

    companion object {
        fun fromRaw(raw: String?): MealSource =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MANUAL
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    val userId: String = "",
    val displayName: String = "Bitevue Member",
    val calorieGoal: Long = 2000L,
    val proteinGoal: Long = 120L,
    val carbsGoal: Long = 220L,
    val fatGoal: Long = 65L,
    val waterGoal: Long = 8L,
    val starterSeeded: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MealEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val category: String = MealCategory.BREAKFAST.name,
    val name: String = "",
    val detail: String = "",
    val calories: Long = 0L,
    val proteinGrams: Long = 0L,
    val carbsGrams: Long = 0L,
    val fatGrams: Long = 0L,
    val source: String = MealSource.MANUAL.name,
    val insight: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val mealCategory: MealCategory
        get() = MealCategory.fromRaw(category)

    val mealSource: MealSource
        get() = MealSource.fromRaw(source)
}

@Keep
@IgnoreExtraProperties
data class DailyHydration(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val glasses: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class PresetMeal(
    val category: MealCategory,
    val name: String,
    val detail: String,
    val calories: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int
)

data class FoodScanResult(
    val dishName: String,
    val detail: String,
    val category: MealCategory,
    val calories: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int,
    val insight: String
)

object CuratedMealPresets {
    val starterMeals: List<PresetMeal> = listOf(
        PresetMeal(
            category = MealCategory.BREAKFAST,
            name = "Oats and banana",
            detail = "Warm rolled oats with sliced banana and seeds",
            calories = 320,
            proteinGrams = 12,
            carbsGrams = 56,
            fatGrams = 7
        ),
        PresetMeal(
            category = MealCategory.LUNCH,
            name = "Rice, dal and vegetables",
            detail = "Steamed rice, yellow lentil dal, and seasonal greens",
            calories = 520,
            proteinGrams = 21,
            carbsGrams = 78,
            fatGrams = 12
        ),
        PresetMeal(
            category = MealCategory.SNACK,
            name = "Crisp apple",
            detail = "Fresh medium orchard apple",
            calories = 95,
            proteinGrams = 1,
            carbsGrams = 25,
            fatGrams = 0
        )
    )

    val quickPresets: List<PresetMeal> = listOf(
        PresetMeal(
            category = MealCategory.BREAKFAST,
            name = "Oats and banana",
            detail = "Rolled oats, banana slices, and chia seeds",
            calories = 320,
            proteinGrams = 12,
            carbsGrams = 56,
            fatGrams = 7
        ),
        PresetMeal(
            category = MealCategory.BREAKFAST,
            name = "Avocado sourdough toast",
            detail = "Toasted sourdough with smashed avocado and poached egg",
            calories = 380,
            proteinGrams = 15,
            carbsGrams = 34,
            fatGrams = 21
        ),
        PresetMeal(
            category = MealCategory.LUNCH,
            name = "Rice, dal and vegetables",
            detail = "Steamed rice, yellow dal, and sautéed veggies",
            calories = 520,
            proteinGrams = 21,
            carbsGrams = 78,
            fatGrams = 12
        ),
        PresetMeal(
            category = MealCategory.LUNCH,
            name = "Grilled herb chicken bowl",
            detail = "Quinoa, roasted greens, herb chicken, and tahini drizzle",
            calories = 540,
            proteinGrams = 42,
            carbsGrams = 48,
            fatGrams = 18
        ),
        PresetMeal(
            category = MealCategory.DINNER,
            name = "Roasted salmon & greens",
            detail = "Lemon-herb salmon fillet with sweet potato and broccoli",
            calories = 590,
            proteinGrams = 39,
            carbsGrams = 42,
            fatGrams = 28
        ),
        PresetMeal(
            category = MealCategory.DINNER,
            name = "Paneer tikka & roti",
            detail = "Spiced grilled paneer with two whole-wheat rotis and salad",
            calories = 510,
            proteinGrams = 28,
            carbsGrams = 46,
            fatGrams = 22
        ),
        PresetMeal(
            category = MealCategory.SNACK,
            name = "Greek yogurt parfait",
            detail = "Unsweetened Greek yogurt, berries, and toasted almonds",
            calories = 210,
            proteinGrams = 17,
            carbsGrams = 20,
            fatGrams = 7
        ),
        PresetMeal(
            category = MealCategory.SNACK,
            name = "Crisp apple & peanut butter",
            detail = "Sliced apple with one tablespoon natural peanut butter",
            calories = 190,
            proteinGrams = 5,
            carbsGrams = 28,
            fatGrams = 8
        )
    )
}

object DateKeyUtils {
    private val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val labelFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

    fun todayKey(): String = keyFormat.format(Date())

    fun dateKeyOffset(daysOffset: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, daysOffset)
        return keyFormat.format(cal.time)
    }

    fun formatDateLabel(dateKey: String): String {
        val today = todayKey()
        val yesterday = dateKeyOffset(-1)
        return when (dateKey) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> runCatching {
                val parsed = keyFormat.parse(dateKey)
                if (parsed != null) labelFormat.format(parsed) else dateKey
            }.getOrDefault(dateKey)
        }
    }

    fun recentDateKeys(daysBack: Int = 6): List<String> =
        (0..daysBack).map { offset -> dateKeyOffset(-offset) }
}
