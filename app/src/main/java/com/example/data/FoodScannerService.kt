package com.example.data

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "FoodScannerService"
private const val MAX_BITMAP_DIM = 1024

class FoodScannerService {
    private val foodNutritionSchema = Schema.obj(
        properties = mapOf(
            "dishName" to Schema.string(
                description = "Short, clear dish name (e.g., Avocado Toast with Egg), max 60 chars"
            ),
            "detail" to Schema.string(
                description = "Concise list of visible ingredients and estimated portion size, max 160 chars"
            ),
            "category" to Schema.enumeration(
                values = listOf("BREAKFAST", "LUNCH", "DINNER", "SNACK"),
                description = "Best matching meal category"
            ),
            "calories" to Schema.integer(
                description = "Estimated total kilocalories (kcal) for the plated portion"
            ),
            "proteinGrams" to Schema.integer(
                description = "Estimated protein in whole grams"
            ),
            "carbsGrams" to Schema.integer(
                description = "Estimated carbohydrates in whole grams"
            ),
            "fatGrams" to Schema.integer(
                description = "Estimated fat in whole grams"
            ),
            "insight" to Schema.string(
                description = "One warm, encouraging 1-sentence nutrition note about this meal, max 180 chars"
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = foodNutritionSchema
                temperature = 0.2f
            }
        )
    }

    suspend fun analyzeFoodPhoto(rawBitmap: Bitmap): Result<FoodScanResult> {
        return try {
            val scaledBitmap = withContext(Dispatchers.Default) {
                downscaleBitmapIfNeeded(rawBitmap, MAX_BITMAP_DIM)
            }
            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        image(scaledBitmap)
                        text(
                            "You are a helpful nutritionist for Bitevue. Identify the food or drink on this plate, " +
                                "estimate realistic calories and macronutrients (protein, carbs, fat in grams) for the visible portion, " +
                                "choose the best meal category, and provide one brief supportive nutrition insight."
                        )
                    }
                )
            }
            val rawJson = response.text
            if (rawJson.isNullOrBlank()) {
                return Result.failure(IllegalStateException("Gemini returned an empty response. Please try another photo."))
            }
            val json = JSONObject(rawJson)
            val result = FoodScanResult(
                dishName = json.optString("dishName", "Scanned meal").trim().ifBlank { "Scanned meal" }.take(80),
                detail = json.optString("detail", "").trim().take(200),
                category = MealCategory.fromRaw(json.optString("category", "LUNCH")),
                calories = json.optInt("calories", 350).coerceIn(0, 5000),
                proteinGrams = json.optInt("proteinGrams", 15).coerceIn(0, 400),
                carbsGrams = json.optInt("carbsGrams", 40).coerceIn(0, 600),
                fatGrams = json.optInt("fatGrams", 12).coerceIn(0, 300),
                insight = json.optString("insight", "").trim().take(240)
            )
            Result.success(result)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Food photo analysis timed out", e)
            Result.failure(Exception("Analysis timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Food photo analysis failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Could not analyze food photo. Please try again."))
        }
    }

    private fun downscaleBitmapIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / maxOf(width, height).toFloat()
        val targetW = (width * ratio).toInt().coerceAtLeast(1)
        val targetH = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }
}
