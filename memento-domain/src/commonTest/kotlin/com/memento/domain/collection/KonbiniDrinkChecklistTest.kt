package com.memento.domain.collection

import com.memento.domain.T1
import com.memento.domain.T2
import com.memento.domain.memento
import com.memento.domain.model.Place
import com.memento.domain.model.Tag
import com.memento.domain.model.TastingNotes
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

    @Test
    fun itemMatchTagsContainOnlyTheItemId() {
        collection.items.forEach { item ->
            assertEquals(listOf(item.id), item.matchTags, "Item ${item.id} must only be tagged by its id")
        }
    }

    @Test
    fun brandOnlyMementoDoesNotCoverAnyItem() {
        val suntory = memento(id = "suntory", place = Place("Konbini", brand = "Suntory"))

        assertEquals(emptySet(), collection.completedItemIds(listOf(suntory)))
        assertEquals(0, collection.calculateCompletionPercentage(listOf(suntory)))
    }

    @Test
    fun itemLabelInTitleCoversItem() {
        val boss = collection.items.single { it.id == "boss-rainbow" }
        val match = memento(id = "m", title = "Tried the boss rainbow mountain today")

        assertTrue(boss.isCoveredBy(match))
        assertEquals(setOf("boss-rainbow"), collection.completedItemIds(listOf(match)))
    }

    @Test
    fun itemLabelInTastingNotesCoversItem() {
        val boss = collection.items.single { it.id == "boss-rainbow" }
        val match = memento(id = "m", tastingNotes = TastingNotes(text = "BOSS Rainbow Mountain notes"))

        assertTrue(boss.isCoveredBy(match))
    }

    @Test
    fun itemIdTagCoversItem() {
        val boss = collection.items.single { it.id == "boss-rainbow" }
        val match = memento(id = "m", tags = listOf(Tag("BOSS-RAINBOW")))

        assertTrue(boss.isCoveredBy(match))
        assertEquals(setOf("boss-rainbow"), collection.completedItemIds(listOf(match)))
    }

    @Test
    fun unrelatedMementoCoversNothing() {
        val unrelated = memento(id = "m", title = "A quiet afternoon")

        assertEquals(emptySet(), collection.completedItemIds(listOf(unrelated)))
    }

    @Test
    fun oneProductEntryCoversExactlyOneItem() {
        val match = memento(
            id = "m",
            title = "BOSS Rainbow Mountain",
            place = Place("Konbini", brand = "Suntory"),
        )

        val completed = collection.completedItemIds(listOf(match))

        assertEquals(setOf("boss-rainbow"), completed)
        assertEquals((1 * 100) / collection.items.size, collection.calculateCompletionPercentage(listOf(match)))
    }
}
