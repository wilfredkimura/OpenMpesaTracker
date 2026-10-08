package com.openmpesa.tracker.ui.settings

import com.openmpesa.tracker.data.model.CategoryPresets

/**
 * State object representing data displayed in the Category Settings screen.
 */
data class SettingUiState(
    /** Built-in permanent categories ("Utilities/Bills/Fees" and "Personal") */
    val defaultCategories: List<String> = CategoryPresets.defaultCategories,

    /** User-created custom spending categories */
    val customCategories: List<String> = emptyList(),

    /** The current text typed into the new category input field */
    val newCategoryInput: String = "",

    /** Optional error notification displayed when an input is invalid or duplicate */
    val errorMessage: String? = null,

    /** Optional success notification displayed when a category is added or removed */
    val successMessage: String? = null,

    /** True when categories are initially loading */
    val isLoading: Boolean = false
)
