package com.memento.domain.collection

import com.memento.domain.T1
import com.memento.domain.T2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KonbiniDrinkChecklistTest {

    private val collection = KonbiniDrinkChecklist.create(T1)

    @Test
    fun collectionIdIsStable() {
        assertEquals("japan-konbini-drinks-2026", KonbiniDrinkChecklist.COLLECTION_ID.value)
        assertEquals(KonbiniDrinkChecklist.COLLECTION_ID, collection.id)
        assertEquals(
            KonbiniDrinkChecklist.COLLECTION_ID,
            KonbiniDrinkChecklist.create(T2).id,
        )
    }

    @Test
    fun createdAtUsesProvidedInstant() {
        assertEquals(T1, collection.createdAt)
        assertEquals(T2, KonbiniDrinkChecklist.create(T2).createdAt)
    }

    @Test
    fun collectionHasCategoriesAndItems() {
        assertTrue(collection.categories.isNotEmpty())
        assertTrue(collection.items.isNotEmpty())
        assertTrue(collection.name.isNotBlank())
    }

    @Test
    fun itemIdsAreUnique() {
        val ids = collection.items.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun itemCategoryIdsReferenceRealCategories() {
        val categoryIds = collection.categories.map { it.id }.toSet()

        collection.items.forEach { item ->
            val categoryId = item.categoryId
            assertTrue(categoryId != null, "Item ${item.id} has no category")
            assertTrue(categoryId in categoryIds, "Item ${item.id} references unknown category $categoryId")
        }
    }

    @Test
    fun everyCategoryHasAtLeastOneItem() {
        val usedCategoryIds = collection.items.mapNotNull { it.categoryId }.toSet()

        collection.categories.forEach { category ->
            assertTrue(category.id in usedCategoryIds, "Category ${category.id} has no items")
        }
    }

    @Test
    fun itemsHaveNonBlankLabelsBrandsAndMatchTags() {
        collection.items.forEach { item ->
            assertTrue(item.label.isNotBlank(), "Item ${item.id} has a blank label")
            assertFalse(item.brand.isNullOrBlank(), "Item ${item.id} has a blank brand")
            assertTrue(item.matchTags.isNotEmpty(), "Item ${item.id} has no match tags")
            assertTrue(item.matchTags.none { it.isBlank() }, "Item ${item.id} has a blank match tag")
        }
    }

    @Test
    fun categoriesHaveUniqueNonBlankIdsAndNames() {
        val ids = collection.categories.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
        collection.categories.forEach { category ->
            assertTrue(category.id.isNotBlank())
            assertTrue(category.name.isNotBlank())
        }
    }
}
