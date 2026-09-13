package com.memento.portability

import kotlin.test.Test
import kotlin.test.assertTrue

class MarkdownJournalGeneratorTest {

    private fun backup(): MementoBackupV1 = MementoBackupV1(
        schemaVersion = 1,
        exportedAt = "2024-04-01T00:00:00Z",
        mementos = listOf(
            BackupMemento(
                id = "m-new",
                title = "Ramen at Ichiran",
                place = BackupPlace(
                    name = "Ichiran",
                    brand = "Ichiran",
                    neighborhood = "Shinjuku",
                    city = "Tokyo",
                    country = "Japan",
                ),
                coordinates = BackupCoordinates(35.0, 139.0, 5.0),
                media = listOf(BackupMedia("media-1", "memory://photo.jpg", "image/jpeg", "2024-03-01T00:00:00Z")),
                rating = BackupRating(4),
                tastingNotes = BackupTastingNotes("Rich tonkotsu broth", listOf("umami", "spicy")),
                tags = listOf("food", "tokyo"),
                occurredAt = "2024-03-01T00:00:00Z",
                createdAt = "2024-03-01T00:00:00Z",
                updatedAt = "2024-03-01T00:00:00Z",
            ),
            BackupMemento(
                id = "m-old",
                title = "Old moment",
                occurredAt = "2024-01-01T00:00:00Z",
                createdAt = "2024-01-01T00:00:00Z",
                updatedAt = "2024-01-01T00:00:00Z",
            ),
        ),
    )

    @Test
    fun rendersTitleSummaryAndStructuredFields() {
        val markdown = MarkdownJournalGenerator.generate(backup())

        assertTrue(markdown.contains("# Memento Journal"), "missing title")
        assertTrue(markdown.contains("_2 moments exported._"), "missing summary")
        assertTrue(markdown.contains("## Ramen at Ichiran"), "missing heading")
        assertTrue(markdown.contains("**Date:** 2024-03-01T00:00:00Z"), "missing date")
        assertTrue(markdown.contains("**Place:** Ichiran"), "missing place")
        assertTrue(markdown.contains("**Brand:** Ichiran"), "missing brand")
        assertTrue(markdown.contains("**City:** Tokyo"), "missing city")
        assertTrue(markdown.contains("**Rating:** \u2605\u2605\u2605\u2605\u2606"), "missing stars")
        assertTrue(markdown.contains("**Coordinates:** 35, 139"), "missing coordinates")
        assertTrue(markdown.contains("**Tasting notes:** Rich tonkotsu broth"), "missing tasting notes")
        assertTrue(markdown.contains("**Flavour tags:** umami, spicy"), "missing flavour tags")
        assertTrue(markdown.contains("**Tags:** #food #tokyo"), "missing tags")
        assertTrue(markdown.contains("![](media/media-1)"), "missing relative image link")
    }

    @Test
    fun ordersMomentsNewestFirst() {
        val markdown = MarkdownJournalGenerator.generate(backup())

        val newest = markdown.indexOf("## Ramen at Ichiran")
        val oldest = markdown.indexOf("## Old moment")
        assertTrue(newest in 0 until oldest, "newest moment should be rendered before the oldest")
    }
}
