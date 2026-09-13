package com.memento.domain.collection

import com.memento.domain.T1
import com.memento.domain.T2
import com.memento.domain.T3
import com.memento.domain.memento
import com.memento.domain.model.ChecklistItem
import com.memento.domain.model.Collection
import com.memento.domain.model.CollectionCategory
import com.memento.domain.model.CollectionId
import com.memento.domain.model.Place
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AchievementsTest {

    private val coffeeCategory = CollectionCategory("cat-coffee", "Coffee", bonusPoints = 25)
    private val teaCategory = CollectionCategory("cat-tea", "Tea", bonusPoints = 30)
    private val boss = ChecklistItem(
        id = "i-boss",
        label = "BOSS Rainbow Mountain",
        categoryId = "cat-coffee",
        brand = "Suntory",
        points = 10,
    )
    private val pocari = ChecklistItem(
        id = "i-pocari",
        label = "Pocari Sweat",
        categoryId = "cat-coffee",
        brand = "Otsuka",
        points = 15,
    )
    private val oi = ChecklistItem(
        id = "i-oi",
        label = "Ito En Oi Ocha",
        categoryId = "cat-tea",
        brand = "Ito En",
        matchTags = listOf("green-tea"),
        points = 20,
    )

    private val collection = Collection(
        id = CollectionId("c1"),
        name = "Konbini Drinks",
        categories = listOf(coffeeCategory, teaCategory),
        items = listOf(boss, pocari, oi),
        createdAt = T1,
    )

    private val bossBrand = memento(id = "m-brand", place = Place("Store", brand = "Suntory"), occurredAt = T1)
    private val pocariBrand = memento(id = "m-pocari", place = Place("Store", brand = "Otsuka"), occurredAt = T2)
    private val oiTag = memento(id = "m-oi", tags = listOf(com.memento.domain.model.Tag("Green-Tea")), occurredAt = T3)

    private fun boardOf(vararg mementos: com.memento.domain.model.Memento) =
        collection.achievementBoard(mementos.toList())

    @Test
    fun itemAchievementUsesEarliestCoveringMemento() {
        val later = memento(id = "later", place = Place("Store", brand = "Suntory"), occurredAt = T3)
        val earlier = memento(id = "earlier", title = "BOSS Rainbow Mountain", occurredAt = T2)

        val bossAchievement = boardOf(later, earlier).achievements.single { it.id == "item:i-boss" }

        assertTrue(bossAchievement.earned)
        assertEquals(T2, bossAchievement.earnedAt)
        assertEquals(1, bossAchievement.progress)
        assertEquals(1, bossAchievement.target)
        assertEquals(10, bossAchievement.points)
        assertEquals(AchievementTier.BRONZE, bossAchievement.tier)
        assertEquals(AchievementKind.ITEM, bossAchievement.kind)
        assertEquals("i-boss", bossAchievement.itemId)
        assertEquals("cat-coffee", bossAchievement.categoryId)
        assertFalse(bossAchievement.isMeta)
    }

    @Test
    fun unearnedItemHasNoTimestampAndZeroProgress() {
        val bossAchievement = boardOf().achievements.single { it.id == "item:i-boss" }

        assertFalse(bossAchievement.earned)
        assertNull(bossAchievement.earnedAt)
        assertEquals(0, bossAchievement.progress)
        assertEquals(0f, bossAchievement.fraction)
    }

    @Test
    fun categoryEarnedOnlyWhenEveryItemIsCovered() {
        val partial = boardOf(bossBrand).achievements.single { it.id == "category:cat-coffee" }
        assertFalse(partial.earned)
        assertEquals(1, partial.progress)
        assertEquals(2, partial.target)
        assertNull(partial.earnedAt)
        assertEquals(AchievementTier.SILVER, partial.tier)
        assertEquals(AchievementKind.CATEGORY, partial.kind)
        assertEquals(25, partial.points)

        val completed = boardOf(bossBrand, pocariBrand).achievements.single { it.id == "category:cat-coffee" }
        assertTrue(completed.earned)
        assertEquals(2, completed.progress)
        assertEquals(T2, completed.earnedAt)
    }

    @Test
    fun metaEarnedOnlyWhenEveryCategoryIsEarned() {
        val board = boardOf(bossBrand, pocariBrand)
        val meta = assertNotNull(board.meta)

        assertFalse(meta.earned)
        assertEquals(1, meta.progress)
        assertEquals(2, meta.target)
        assertNull(meta.earnedAt)
        assertTrue(meta.isMeta)
        assertEquals(AchievementKind.META, meta.kind)
        assertEquals(AchievementTier.GOLD, meta.tier)
        assertEquals(155, meta.points)
    }

    @Test
    fun metaEarnedWhenAllCategoriesCompleteWithLatestTimestamp() {
        val board = boardOf(bossBrand, pocariBrand, oiTag)
        val meta = assertNotNull(board.meta)

        assertTrue(meta.earned)
        assertEquals(2, meta.progress)
        assertEquals(2, meta.target)
        assertEquals(T3, meta.earnedAt)
        assertEquals(meta, board.achievements.last())
    }

    @Test
    fun pointsTotalsReflectEarnedAchievements() {
        val empty = boardOf()
        assertEquals(255, empty.totalPoints)
        assertEquals(0, empty.earnedPoints)
        assertEquals(0, empty.earnedCount)
        assertEquals(6, empty.totalCount)
        assertEquals(0, empty.percentage)
        assertFalse(empty.isComplete)

        val oneItem = boardOf(bossBrand)
        assertEquals(10, oneItem.earnedPoints)
        assertEquals(1, oneItem.earnedCount)
        assertEquals(3, oneItem.percentage)

        val everything = boardOf(bossBrand, pocariBrand, oiTag)
        assertEquals(255, everything.earnedPoints)
        assertEquals(6, everything.earnedCount)
        assertEquals(100, everything.percentage)
        assertTrue(everything.isComplete)
    }

    @Test
    fun achievementsAreOrderedCategoriesThenItemsThenMeta() {
        val ids = boardOf(bossBrand, pocariBrand, oiTag).achievements.map { it.id }

        assertEquals(
            listOf(
                "category:cat-coffee",
                "category:cat-tea",
                "item:i-boss",
                "item:i-pocari",
                "item:i-oi",
                "meta:c1",
            ),
            ids,
        )
    }

    @Test
    fun achievementIdsAreUnique() {
        val ids = boardOf(bossBrand, pocariBrand, oiTag).achievements.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun emptyCollectionHasEmptyBoard() {
        val empty = Collection(id = CollectionId("empty"), name = "Empty", createdAt = T1)
        val board = empty.achievementBoard(listOf(bossBrand))

        assertTrue(board.achievements.isEmpty())
        assertNull(board.meta)
        assertEquals(0, board.totalPoints)
        assertEquals(0, board.earnedPoints)
        assertEquals(0, board.totalCount)
        assertEquals(0, board.earnedCount)
        assertEquals(0, board.percentage)
        assertFalse(board.isComplete)
    }

    @Test
    fun categoryWithoutItemsIsNeverEarnedAndHasZeroFraction() {
        val emptyCategory = CollectionCategory("cat-empty", "Empty", bonusPoints = 5)
        val withEmpty = collection.copy(categories = listOf(emptyCategory))

        val board = withEmpty.achievementBoard(emptyList())
        val category = board.achievements.single { it.id == "category:cat-empty" }

        assertFalse(category.earned)
        assertEquals(0, category.target)
        assertEquals(0, category.progress)
        assertEquals(0f, category.fraction)
    }

    @Test
    fun collectionWithoutCategoriesHasNoMetaButStillHasItems() {
        val noCategories = collection.copy(categories = emptyList())

        val board = noCategories.achievementBoard(listOf(bossBrand))

        assertNull(board.meta)
        assertTrue(board.achievements.none { it.kind == AchievementKind.META })
        assertEquals(3, board.achievements.size)
        assertEquals(10, board.earnedPoints)
    }

    @Test
    fun existingProgressBehaviourIsUnchanged() {
        val progress = collection.progress(listOf(bossBrand))

        assertEquals(3, progress.totalItems)
        assertEquals(1, progress.completedItems)
        assertEquals(33, progress.percentage)
        assertEquals(setOf("i-boss"), progress.completedItemIds)
    }
}
