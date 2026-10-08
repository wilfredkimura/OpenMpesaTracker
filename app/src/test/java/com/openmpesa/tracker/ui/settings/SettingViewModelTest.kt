package com.openmpesa.tracker.ui.settings

import com.openmpesa.tracker.data.repository.DefaultCategoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying [SettingViewModel] logic:
 * user input handling, validation, category creation, deletion, and error states.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_hasDefaultCategoriesAndEmptyInput() = runTest {
        val repo = DefaultCategoryRepository()
        val viewModel = SettingViewModel(repo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.defaultCategories.size)
        assertTrue(state.defaultCategories.contains("Utilities/Bills/Fees"))
        assertTrue(state.defaultCategories.contains("Personal"))
        assertTrue(state.customCategories.isEmpty())
        assertEquals("", state.newCategoryInput)
        assertNull(state.errorMessage)
        assertNull(state.successMessage)
    }

    @Test
    fun onNewCategoryChange_updatesInputAndClearsMessages() = runTest {
        val repo = DefaultCategoryRepository()
        val viewModel = SettingViewModel(repo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onNewCategoryChange("Groceries")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Groceries", viewModel.uiState.value.newCategoryInput)
    }

    @Test
    fun addCategory_validName_succeedsAndClearsInput() = runTest {
        val repo = DefaultCategoryRepository()
        val viewModel = SettingViewModel(repo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onNewCategoryChange("Groceries")
        viewModel.addCategory()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.newCategoryInput)
        assertEquals(listOf("Groceries"), state.customCategories)
        assertNull(state.errorMessage)
        assertNotNull(state.successMessage)
        assertTrue(state.successMessage!!.contains("Groceries"))
    }

    @Test
    fun addCategory_blankName_setsErrorMessage() = runTest {
        val repo = DefaultCategoryRepository()
        val viewModel = SettingViewModel(repo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onNewCategoryChange("   ")
        viewModel.addCategory()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.customCategories.isEmpty())
        assertEquals("Please enter a category name.", state.errorMessage)
        assertNull(state.successMessage)
    }

    @Test
    fun addCategory_matchesDefaultCategory_setsErrorMessage() = runTest {
        val repo = DefaultCategoryRepository()
        val viewModel = SettingViewModel(repo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onNewCategoryChange("Personal")
        viewModel.addCategory()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.customCategories.isEmpty())
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("already a default category"))
    }

    @Test
    fun addCategory_duplicateCustomCategory_setsErrorMessage() = runTest {
        val repo = DefaultCategoryRepository(initialCustomCategories = setOf("Groceries"))
        val viewModel = SettingViewModel(repo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onNewCategoryChange("Groceries")
        viewModel.addCategory()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("already exists"))
    }

    @Test
    fun deleteCategory_customCategory_succeeds() = runTest {
        val repo = DefaultCategoryRepository(initialCustomCategories = setOf("Groceries"))
        val viewModel = SettingViewModel(repo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.deleteCategory("Groceries")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.customCategories.isEmpty())
        assertNotNull(state.successMessage)
        assertNull(state.errorMessage)
    }

    @Test
    fun deleteCategory_defaultCategory_fails() = runTest {
        val repo = DefaultCategoryRepository()
        val viewModel = SettingViewModel(repo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.deleteCategory("Personal")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("Cannot delete built-in default categories"))
    }

    @Test
    fun dismissMessages_clearsErrorAndSuccess() = runTest {
        val repo = DefaultCategoryRepository()
        val viewModel = SettingViewModel(repo)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onNewCategoryChange("")
        viewModel.addCategory()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.dismissMessages()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.successMessage)
    }
}
