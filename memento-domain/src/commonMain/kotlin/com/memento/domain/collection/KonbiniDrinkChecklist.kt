package com.memento.domain.collection

import com.memento.domain.model.ChecklistItem
import com.memento.domain.model.Collection
import com.memento.domain.model.CollectionCategory
import com.memento.domain.model.CollectionId
import kotlinx.datetime.Instant

/**
 * Seed factory for the flagship "Japan Konbini Drinks 2026" checklist.
 */
object KonbiniDrinkChecklist {
    val COLLECTION_ID = CollectionId("japan-konbini-drinks-2026")

    val CATEGORY_CANNED_COFFEE = "canned-coffee"
    val CATEGORY_GREEN_TEA = "green-tea"
    val CATEGORY_SPORTS = "sports-drinks"
    val CATEGORY_MILK_TEA = "milk-tea"
    val CATEGORY_FRUIT_SODA = "fruit-soda"
    val CATEGORY_WATER_BARLEY = "water-barley"

    fun create(now: Instant): Collection = Collection(
        id = COLLECTION_ID,
        name = "Japan Konbini Drinks 2026",
        description = "Taste every iconic convenience-store drink in Japan, one can at a time.",
        categories = listOf(
            CollectionCategory(CATEGORY_CANNED_COFFEE, "Canned Coffee", bonusPoints = 25),
            CollectionCategory(CATEGORY_GREEN_TEA, "Green Tea", bonusPoints = 25),
            CollectionCategory(CATEGORY_SPORTS, "Sports Drinks", bonusPoints = 25),
            CollectionCategory(CATEGORY_MILK_TEA, "Milk Tea", bonusPoints = 25),
            CollectionCategory(CATEGORY_FRUIT_SODA, "Fruit & Soda", bonusPoints = 25),
            CollectionCategory(CATEGORY_WATER_BARLEY, "Water & Barley Tea", bonusPoints = 25),
        ),
        items = listOf(
            item("boss-rainbow", "BOSS Rainbow Mountain", CATEGORY_CANNED_COFFEE, "Suntory"),
            item("boss-black", "BOSS Black", CATEGORY_CANNED_COFFEE, "Suntory"),
            item("georgia-emerald", "Georgia Emerald Mountain Blend", CATEGORY_CANNED_COFFEE, "Coca-Cola"),
            item("wonda-kinn", "Wonda Kinn no Coffee", CATEGORY_CANNED_COFFEE, "Asahi"),
            item("dydo-blend", "Dydo Blend Coffee", CATEGORY_CANNED_COFFEE, "Dydo"),
            item("tully-coffee", "Tully's Barista's Black", CATEGORY_CANNED_COFFEE, "Tully's"),
            item("ito-en-oi", "Ito En Oi Ocha", CATEGORY_GREEN_TEA, "Ito En"),
            item("ito-en-kokucha", "Ito En Kokucha", CATEGORY_GREEN_TEA, "Ito En"),
            item("ayatuki-fine", "Ayataka", CATEGORY_GREEN_TEA, "Coca-Cola"),
            item("namacha", "Kirin Namacha", CATEGORY_GREEN_TEA, "Kirin"),
            item("pocari-sweat", "Pocari Sweat", CATEGORY_SPORTS, "Otsuka"),
            item("aquarius", "Aquarius", CATEGORY_SPORTS, "Coca-Cola"),
            item("da-da", "DADA", CATEGORY_SPORTS, "Suntory"),
            item("gatorade", "Gatorade", CATEGORY_SPORTS, "Suntory"),
            item("kirin-milk-tea", "Kirin Gogo no Kocha", CATEGORY_MILK_TEA, "Kirin"),
            item("afternoon-tea", "Afternoon Tea Milk Tea", CATEGORY_MILK_TEA, "Suntory"),
            item("royal-milk-tea", "Royal Milk Tea", CATEGORY_MILK_TEA, "Pokka Sapporo"),
            item("calpis-water", "Calpis Water", CATEGORY_FRUIT_SODA, "Asahi"),
            item("cc-lemon", "C.C. Lemon", CATEGORY_FRUIT_SODA, "Suntory"),
            item("fanta-grape", "Fanta Grape", CATEGORY_FRUIT_SODA, "Coca-Cola"),
            item("melonsoda", "Suntory Melon Soda", CATEGORY_FRUIT_SODA, "Suntory"),
            item("tennensui", "Suntory Tennensui", CATEGORY_WATER_BARLEY, "Suntory"),
            item("rooibos-barley", "Kirin Rooibos Barley Tea", CATEGORY_WATER_BARLEY, "Kirin"),
            item("ikenaga-barley", "Ikenaga Barley Tea", CATEGORY_WATER_BARLEY, "Ikenaga"),
        ),
        createdAt = now,
        metaAchievementName = "Konbini Grand Slam",
        metaAchievementDescription = "Drain every category of the Japan Konbini Drinks 2026 board.",
    )

    private fun item(
        id: String,
        label: String,
        categoryId: String,
        brand: String,
        points: Int = 10,
    ) = ChecklistItem(
        id = id,
        label = label,
        categoryId = categoryId,
        brand = brand,
        matchTags = listOf(id, brand.lowercase().replace(" ", "-")),
        points = points,
    )
}
