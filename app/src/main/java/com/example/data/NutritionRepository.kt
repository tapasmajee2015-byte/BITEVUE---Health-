package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class NutritionRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        if (expectedUserId != null && uid != expectedUserId) {
            throw IllegalStateException("Signed-in user changed; refusing cross-user operation.")
        }
        return uid
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeMealsForDate(userId: String, dateKey: String): Flow<List<MealEntry>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/meals"
        emitAll(
            db.collection("users").document(uid).collection("meals")
                .whereEqualTo("dateKey", dateKey)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MealEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeHydrationForDate(userId: String, dateKey: String): Flow<Int> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/hydration/$dateKey"
        emitAll(
            db.collection("users").document(uid).collection("hydration").document(dateKey)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        (snapshot.getLong("glasses") ?: 0L).toInt().coerceIn(0, 16)
                    } else {
                        0
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureProfileAndSeedStarterData(
        userId: String,
        initialDisplayName: String,
        todayDateKey: String
    ): Result<Unit> {
        val profilePath = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val userDocRef = db.collection("users").document(uid)
            val existingSnapshot = userDocRef.get().await()

            val safeName = initialDisplayName.trim().ifBlank { "Bitevue Member" }.take(60)
            val needsSeed: Boolean
            if (!existingSnapshot.exists()) {
                val createPayload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "calorieGoal" to 2000L,
                    "proteinGoal" to 120L,
                    "carbsGoal" to 220L,
                    "fatGoal" to 65L,
                    "waterGoal" to 8L,
                    "starterSeeded" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userDocRef.set(createPayload).await()
                needsSeed = true
            } else {
                needsSeed = existingSnapshot.getBoolean("starterSeeded") != true
            }

            if (needsSeed) {
                val batch = db.batch()
                val mealsCol = userDocRef.collection("meals")
                CuratedMealPresets.starterMeals.forEach { preset ->
                    val mealDoc = mealsCol.document()
                    batch.set(
                        mealDoc,
                        mapOf(
                            "userId" to uid,
                            "dateKey" to todayDateKey,
                            "category" to preset.category.name,
                            "name" to preset.name.take(80),
                            "detail" to preset.detail.take(200),
                            "calories" to preset.calories.toLong(),
                            "proteinGrams" to preset.proteinGrams.toLong(),
                            "carbsGrams" to preset.carbsGrams.toLong(),
                            "fatGrams" to preset.fatGrams.toLong(),
                            "source" to MealSource.PRESET.name,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                val hydrationDoc = userDocRef.collection("hydration").document(todayDateKey)
                val existingHydration = hydrationDoc.get().await()
                if (!existingHydration.exists()) {
                    batch.set(
                        hydrationDoc,
                        mapOf(
                            "userId" to uid,
                            "dateKey" to todayDateKey,
                            "glasses" to 3L,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                batch.update(
                    userDocRef,
                    mapOf(
                        "starterSeeded" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, profilePath)
            Result.failure(e)
        }
    }

    suspend fun addMeal(
        userId: String,
        dateKey: String,
        category: MealCategory,
        name: String,
        detail: String,
        calories: Int,
        proteinGrams: Int,
        carbsGrams: Int,
        fatGrams: Int,
        source: MealSource,
        insight: String? = null
    ): Result<String> {
        var docPath = "users/$userId/meals"
        return try {
            val uid = requireUserId(userId)
            val trimmedName = name.trim()
            require(trimmedName.isNotEmpty() && trimmedName.length <= 80) {
                "Meal name must be between 1 and 80 characters."
            }
            val safeDetail = detail.trim().take(200)
            val safeCalories = calories.coerceIn(0, 5000).toLong()
            val safeProtein = proteinGrams.coerceIn(0, 400).toLong()
            val safeCarbs = carbsGrams.coerceIn(0, 600).toLong()
            val safeFat = fatGrams.coerceIn(0, 300).toLong()
            val cleanInsight = insight?.trim()?.takeIf { it.isNotEmpty() }?.take(240)

            val docRef = db.collection("users").document(uid).collection("meals").document()
            docPath = docRef.path

            val payload = mapOf<String, Any?>(
                "userId" to uid,
                "dateKey" to dateKey,
                "category" to category.name,
                "name" to trimmedName,
                "detail" to safeDetail,
                "calories" to safeCalories,
                "proteinGrams" to safeProtein,
                "carbsGrams" to safeCarbs,
                "fatGrams" to safeFat,
                "source" to source.name,
                "insight" to cleanInsight,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteMeal(userId: String, mealId: String): Result<Unit> {
        val docPath = "users/$userId/meals/$mealId"
        return try {
            val uid = requireUserId(userId)
            require(mealId.isNotBlank()) { "Meal ID must not be blank." }
            db.collection("users").document(uid)
                .collection("meals").document(mealId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun setHydration(userId: String, dateKey: String, glasses: Int): Result<Unit> {
        val docPath = "users/$userId/hydration/$dateKey"
        return try {
            val uid = requireUserId(userId)
            val clamped = glasses.coerceIn(0, 16).toLong()
            val docRef = db.collection("users").document(uid)
                .collection("hydration").document(dateKey)
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                docRef.update(
                    mapOf(
                        "glasses" to clamped,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "dateKey" to dateKey,
                        "glasses" to clamped,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateProfileAndGoals(
        userId: String,
        displayName: String,
        calorieGoal: Int,
        proteinGoal: Int,
        carbsGoal: Int,
        fatGoal: Int,
        waterGoal: Int
    ): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val safeName = displayName.trim().ifBlank { "Bitevue Member" }.take(60)
            val safeCalories = calorieGoal.coerceIn(800, 6000).toLong()
            val safeProtein = proteinGoal.coerceIn(10, 400).toLong()
            val safeCarbs = carbsGoal.coerceIn(10, 800).toLong()
            val safeFat = fatGoal.coerceIn(10, 300).toLong()
            val safeWater = waterGoal.coerceIn(1, 16).toLong()

            val docRef = db.collection("users").document(uid)
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                docRef.update(
                    mapOf(
                        "displayName" to safeName,
                        "calorieGoal" to safeCalories,
                        "proteinGoal" to safeProtein,
                        "carbsGoal" to safeCarbs,
                        "fatGoal" to safeFat,
                        "waterGoal" to safeWater,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to safeName,
                        "calorieGoal" to safeCalories,
                        "proteinGoal" to safeProtein,
                        "carbsGoal" to safeCarbs,
                        "fatGoal" to safeFat,
                        "waterGoal" to safeWater,
                        "starterSeeded" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }
}
