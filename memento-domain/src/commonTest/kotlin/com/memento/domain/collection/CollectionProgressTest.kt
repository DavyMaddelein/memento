package com.memento.domain.collection

import com.memento.domain.T1
import com.memento.domain.memento
import com.memento.domain.model.ChecklistItem
import com.memento.domain.model.Collection
import com.memento.domain.model.CollectionCategory
import com.memento.domain.model.CollectionId
import com.memento.domain.model.Place
import com.memento.domain.model.Tag
import com.memento.domain.model.TastingNotes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CollectionProgressTest {

    private val coffeeCategory = CollectionCategory("cat-coffee", "Coffee")
    private val teaCategory = CollectionCategory("cat-tea", "Tea")
    private val boss = ChecklistItem("i-boss", "BOSS Rainbow Mountain", "cat-coffee", brand = "Suntory")
    private val oi = ChecklistItem(
        id = "i-oi",
        label = "Ito En Oi Ocha",
        categoryId = "cat-tea",
        brand = "Ito En",
        matchTags = listOf("green-tea"),
    )
    private val pocari = ChecklistItem("i-pocari", "Pocari Sweat", "cat-coffee", brand = "Otsuka")

    private val collection = Collection(
        id = CollectionId("c1"),
        name = "Konbini Drinks",
        categories = listOf(coffeeCategory, teaCategory),
        items = listOf(boss, oi, pocari),
        createdAt = T1,
    )

    private val brandMatch = memento(id = "brand", place = Place("Store", brand = "Suntory"))
    private val tagMatch = memento(id = "tag", tags = listOf(Tag("Green-Tea")))
    private val labelMatch = memento(id = "label", title = "I drank Pocari Sweat today")

    @Test
    fun isCoveredByBrandIsCaseInsensitive() {
        assertTrue(boss.isCoveredBy(memento(place = Place("Store", brand = "suntory"))))
    }

    @Test
    fun isCoveredBySharedMatchTagIsCaseInsensitive() {
        assertTrue(oi.isCoveredBy(memento(tags = listOf(Tag("GREEN-TEA")))))
    }

    @Test
    fun isCoveredByLabelInTitle() {
        assertTrue(pocari.isCoveredBy(memento(title = "I drank Pocari Sweat today")))
    }

    @Test
    fun isCoveredByLabelInTastingNotes() {
        val item = ChecklistItem("i-note", "Caramel finish", categoryId = "cat-coffee")
        val tastingMemento = memento(tastingNotes = TastingNotes(text = "A caramel finish"))

        assertTrue(item.isCoveredBy(tastingMemento))
    }

    @Test
    fun unrelatedMementoDoesNotCoverItem() {
        assertFalse(boss.isCoveredBy(memento(id = "x", title = "Something else")))
    }

    @Test
    fun completedItemIdsReflectsAllCoverageStrategies() {
        val completed = collection.completedItemIds(listOf(brandMatch, tagMatch, labelMatch))

        assertEquals(setOf("i-boss", "i-oi", "i-pocari"), completed)
    }

    @Test
    fun percentageWithOneOfThreeIsThirtyThree() {
        assertEquals(33, collection.calculateCompletionPercentage(listOf(brandMatch)))
    }

    @Test
    fun percentageWithAllItemsIsOneHundred() {
        assertEquals(100, collection.calculateCompletionPercentage(listOf(brandMatch, tagMatch, labelMatch)))
    }

    @Test
    fun emptyCollectionHasZeroPercentage() {
        val empty = collection.copy(items = emptyList())

        assertEquals(0, empty.calculateCompletionPercentage(listOf(brandMatch)))
        assertEquals(0, empty.progress(listOf(brandMatch)).percentage)
    }

    @Test
    fun coveredCategoriesFollowCoveredItems() {
        assertContentEquals(listOf(coffeeCategory), collection.getCoveredCategories(listOf(brandMatch)))
        assertContentEquals(listOf(teaCategory), collection.getCoveredCategories(listOf(tagMatch)))
        assertContentEquals(
            listOf(coffeeCategory, teaCategory),
            collection.getCoveredCategories(listOf(brandMatch, tagMatch, labelMatch)),
        )
    }

    @Test
    fun coveredBrandsComeFromMementoPlaces() {
        assertEquals(setOf("Suntory"), collection.getCoveredBrands(listOf(brandMatch)))
        assertEquals(emptySet(), collection.getCoveredBrands(listOf(labelMatch)))
    }

    @Test
    fun progressAggregatesOverallAndPerCategory() {
        val progress = collection.progress(listOf(brandMatch))

        assertEquals(CollectionId("c1"), progress.collectionId)
        assertEquals(3, progress.totalItems)
        assertEquals(1, progress.completedItems)
        assertEquals(setOf("i-boss"), progress.completedItemIds)
        assertEquals(33, progress.percentage)

        val coffee = progress.categories.single { it.categoryId == "cat-coffee" }
        assertEquals("Coffee", coffee.name)
        assertEquals(2, coffee.total)
        assertEquals(1, coffee.completed)
        assertEquals(50, coffee.percentage)

        val tea = progress.categories.single { it.categoryId == "cat-tea" }
        assertEquals(1, tea.total)
        assertEquals(0, tea.completed)
        assertEquals(0, tea.percentage)
    }

    @Test
    fun categoryWithNoItemsHasZeroPercentage() {
        val emptyCategory = CategoryProgress("cat-empty", "Empty", total = 0, completed = 0)

        assertEquals(0, emptyCategory.percentage)
    }
}
