package com.openmpesa.tracker.data.repository

import android.content.SharedPreferences
import com.openmpesa.tracker.data.model.CategoryPresets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Repository interface for managing transaction categories.
 *
 * Exposes the immutable default categories ("Utilities/Bills/Fees" and "Personal")
 * and allows users to define, view, and delete their own custom spending categories.
 */
interface CategoryRepository {

    /**
     * Emits the complete list of available categories:
     * default presets first, followed by any user-created custom categories.
     */
    fun getCategories(): Flow<List<String>>

    /**
     * Emits only user-created custom categories.
     */
    fun getCustomCategories(): Flow<List<String>>

    /**
     * Adds a new custom category.
     *
     * @param name The category name entered by the user.
     * @return True if the category was successfully added, or false if it is invalid or already exists.
     */
    suspend fun addCategory(name: String): Boolean

    /**
     * Removes a user-created custom category.
     *
     * @param name The category name to delete.
     * @return True if successfully deleted, false if the category is a system default or was not found.
     */
    suspend fun deleteCategory(name: String): Boolean

    /**
     * Returns the built-in system default categories.
     */
    fun getDefaultCategories(): List<String>
}

/**
 * Concrete implementation of [CategoryRepository] backed by Android [SharedPreferences]
 * (or an in-memory set when testing).
 *
 * Ensures changes to custom categories persist across app restarts and updates
 * reactively for all screens observing category lists.
 *
 * @param preferences Optional Android SharedPreferences for persistence.
 * @param initialCustomCategories Optional seed categories for tests.
 */
class DefaultCategoryRepository(
    private val preferences: SharedPreferences? = null,
    initialCustomCategories: Set<String> = emptySet()
) : CategoryRepository {

    private val prefKey = "custom_categories_set"

    // Load persisted custom categories or fallback to initial test set
    private val _customCategoriesFlow = MutableStateFlow<List<String>>(
        loadSavedCategories(initialCustomCategories)
    )

    private fun loadSavedCategories(seed: Set<String>): List<String> {
        return if (preferences != null) {
            val savedSet = preferences.getStringSet(prefKey, emptySet()) ?: emptySet()
            savedSet.toList().sorted()
        } else {
            seed.toList().sorted()
        }
    }

    private fun persistCategories(list: List<String>) {
        preferences?.edit()?.putStringSet(prefKey, list.toSet())?.apply()
        _customCategoriesFlow.value = list
    }

    override fun getCategories(): Flow<List<String>> {
        return _customCategoriesFlow.asStateFlow().map { customList ->
            (CategoryPresets.defaultCategories + customList).distinct()
        }
    }

    override fun getCustomCategories(): Flow<List<String>> {
        return _customCategoriesFlow.asStateFlow()
    }

    override suspend fun addCategory(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            return false
        }

        // Check if name conflicts with default categories (case-insensitive)
        val matchesDefault = CategoryPresets.defaultCategories.any {
            it.equals(trimmed, ignoreCase = true)
        }
        if (matchesDefault) {
            return false
        }

        // Check if name already exists in custom categories (case-insensitive)
        val currentCustom = _customCategoriesFlow.value
        val alreadyExists = currentCustom.any {
            it.equals(trimmed, ignoreCase = true)
        }
        if (alreadyExists) {
            return false
        }

        val updated = (currentCustom + trimmed).sorted()
        persistCategories(updated)
        return true
    }

    override suspend fun deleteCategory(name: String): Boolean {
        val trimmed = name.trim()

        // Disallow deleting system defaults
        val isDefault = CategoryPresets.defaultCategories.any {
            it.equals(trimmed, ignoreCase = true)
        }
        if (isDefault) {
            return false
        }

        val currentCustom = _customCategoriesFlow.value
        val filtered = currentCustom.filterNot { it.equals(trimmed, ignoreCase = true) }
        if (filtered.size == currentCustom.size) {
            // Category was not present
            return false
        }

        persistCategories(filtered)
        return true
    }

    override fun getDefaultCategories(): List<String> {
        return CategoryPresets.defaultCategories
    }
}
