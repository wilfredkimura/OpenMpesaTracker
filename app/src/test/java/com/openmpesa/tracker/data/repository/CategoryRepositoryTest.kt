package com.openmpesa.tracker.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying [CategoryRepository] and [DefaultCategoryRepository] operations:
 * category retrieval, validation, addition, and deletion.
 */
class CategoryRepositoryTest {

    @Test
    fun getCategories_initiallyContainsOnlyDefaultCategories() = runTest {
        val repo = DefaultCategoryRepository()

        val allCategories = repo.getCategories().first()
        val defaultCategories = repo.getDefaultCategories()

        assertEquals(2, allCategories.size)
        assertEquals(defaultCategories, allCategories)
        assertTrue(allCategories.contains("Utilities/Bills/Fees"))
        assertTrue(allCategories.contains("Personal"))
    }

    @Test
    fun addCategory_validName_succeedsAndUpdatesFlows() = runTest {
        val repo = DefaultCategoryRepository()

        val result = repo.addCategory("Groceries")
        assertTrue("Adding valid category should return true", result)

        val customList = repo.getCustomCategories().first()
        assertEquals(listOf("Groceries"), customList)

        val allList = repo.getCategories().first()
        assertEquals(3, allList.size)
        assertTrue(allList.contains("Groceries"))
        assertTrue(allList.contains("Utilities/Bills/Fees"))
        assertTrue(allList.contains("Personal"))
    }

    @Test
    fun addCategory_blankName_failsAndDoesNotAdd() = runTest {
        val repo = DefaultCategoryRepository()

        assertFalse(repo.addCategory(""))
        assertFalse(repo.addCategory("   "))

        val customList = repo.getCustomCategories().first()
        assertTrue(customList.isEmpty())
    }

    @Test
    fun addCategory_matchingDefaultCategory_fails() = runTest {
        val repo = DefaultCategoryRepository()

        assertFalse(repo.addCategory("Personal"))
        assertFalse(repo.addCategory("personal"))
        assertFalse(repo.addCategory("Utilities/Bills/Fees"))
        assertFalse(repo.addCategory("utilities/bills/fees"))

        val customList = repo.getCustomCategories().first()
        assertTrue(customList.isEmpty())
    }

    @Test
    fun addCategory_duplicateCustomCategory_fails() = runTest {
        val repo = DefaultCategoryRepository()

        assertTrue(repo.addCategory("Shopping"))
        assertFalse("Duplicate custom category should fail", repo.addCategory("Shopping"))
        assertFalse("Case-insensitive duplicate should fail", repo.addCategory("shopping"))

        val customList = repo.getCustomCategories().first()
        assertEquals(1, customList.size)
    }

    @Test
    fun deleteCategory_customCategory_succeeds() = runTest {
        val repo = DefaultCategoryRepository(initialCustomCategories = setOf("Groceries", "Fuel"))

        val initialCustom = repo.getCustomCategories().first()
        assertEquals(2, initialCustom.size)

        val deleted = repo.deleteCategory("Groceries")
        assertTrue(deleted)

        val updatedCustom = repo.getCustomCategories().first()
        assertEquals(listOf("Fuel"), updatedCustom)
        assertFalse(updatedCustom.contains("Groceries"))
    }

    @Test
    fun deleteCategory_defaultCategory_fails() = runTest {
        val repo = DefaultCategoryRepository()

        assertFalse(repo.deleteCategory("Personal"))
        assertFalse(repo.deleteCategory("Utilities/Bills/Fees"))

        val allCategories = repo.getCategories().first()
        assertEquals(2, allCategories.size)
    }

    @Test
    fun deleteCategory_nonExistentCategory_fails() = runTest {
        val repo = DefaultCategoryRepository()

        assertFalse(repo.deleteCategory("NonExistentCategory"))
    }
}
