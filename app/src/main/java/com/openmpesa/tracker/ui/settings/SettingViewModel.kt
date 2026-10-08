package com.openmpesa.tracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.openmpesa.tracker.data.model.CategoryPresets
import com.openmpesa.tracker.data.repository.CategoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel managing custom category creation, viewing, and deletion in the Settings screen.
 *
 * @param repository The category repository for reading and persisting custom categories.
 */
class SettingViewModel(
    private val repository: CategoryRepository
) : ViewModel() {

    private val _newCategoryInput = MutableStateFlow("")
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _successMessage = MutableStateFlow<String?>(null)

    /**
     * Unified reactive UI state observing repository categories and user input.
     */
    val uiState: StateFlow<SettingUiState> = combine(
        repository.getCustomCategories(),
        _newCategoryInput,
        _errorMessage,
        _successMessage
    ) { customCategories, input, error, success ->
        SettingUiState(
            defaultCategories = repository.getDefaultCategories(),
            customCategories = customCategories,
            newCategoryInput = input,
            errorMessage = error,
            successMessage = success,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingUiState(isLoading = true)
    )

    /**
     * Updates the text in the category input text field.
     */
    fun onNewCategoryChange(input: String) {
        _newCategoryInput.value = input
        _errorMessage.value = null
        _successMessage.value = null
    }

    /**
     * Submits the current input to create a new custom category.
     */
    fun addCategory() {
        val candidate = _newCategoryInput.value.trim()
        if (candidate.isBlank()) {
            _errorMessage.value = "Please enter a category name."
            return
        }

        // Check against default categories
        val matchesDefault = CategoryPresets.defaultCategories.any {
            it.equals(candidate, ignoreCase = true)
        }
        if (matchesDefault) {
            _errorMessage.value = "\"$candidate\" is already a default category."
            return
        }

        viewModelScope.launch {
            val added = repository.addCategory(candidate)
            if (added) {
                _newCategoryInput.value = ""
                _errorMessage.value = null
                _successMessage.value = "Category \"$candidate\" added successfully."
            } else {
                _errorMessage.value = "A category named \"$candidate\" already exists."
            }
        }
    }

    /**
     * Deletes a user-created custom category.
     */
    fun deleteCategory(name: String) {
        viewModelScope.launch {
            val deleted = repository.deleteCategory(name)
            if (deleted) {
                _successMessage.value = "Category \"$name\" deleted."
                _errorMessage.value = null
            } else {
                _errorMessage.value = "Cannot delete built-in default categories."
            }
        }
    }

    /**
     * Clears all feedback messages.
     */
    fun dismissMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    /**
     * Factory for constructing [SettingViewModel] with repository dependencies.
     */
    class Factory(
        private val repository: CategoryRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingViewModel::class.java)) {
                return SettingViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
