package com.memento.portability

import com.memento.domain.model.CollectionId
import com.memento.domain.model.Coordinates
import com.memento.domain.model.MediaId
import com.memento.domain.model.MediaReference
import com.memento.domain.model.Memento
import com.memento.domain.model.MementoId
import com.memento.domain.model.Place
import com.memento.domain.model.Rating
import com.memento.domain.model.Tag
import com.memento.domain.model.TastingNotes
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Versioned, transport-safe backup envelope. The JSON produced from this type is written as
 * `mementos.json` inside an export archive and is the single source of truth for structural
 * round-tripping (markdown is a derived, human-facing view).
 *
 * Only [schemaVersion] `1` is currently defined; importers must reject anything greater.
 */
@Serializable
data class MementoBackupV1(
    @SerialName("schema_version") val schemaVersion: Int = SCHEMA_VERSION,
    @SerialName("exported_at") val exportedAt: String,
    val mementos: List<BackupMemento> = emptyList(),
) {
    companion object {
        const val SCHEMA_VERSION: Int = 1
    }
}

@Serializable
data class BackupMemento(
    val id: String,
    val title: String = "",
    val reflection: String = "",
    val coordinates: BackupCoordinates? = null,
    val place: BackupPlace? = null,
    val media: List<BackupMedia> = emptyList(),
    val rating: BackupRating? = null,
    val tastingNotes: BackupTastingNotes? = null,
    val tags: List<String> = emptyList(),
    val collections: List<String> = emptyList(),
    val priceMinorUnits: Long? = null,
    val currencyCode: String? = null,
    val occurredAt: String,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class BackupCoordinates(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double? = null,
)

@Serializable
data class BackupPlace(
    val name: String,
    val brand: String? = null,
    val neighborhood: String? = null,
    val city: String? = null,
    val country: String? = null,
)

@Serializable
data class BackupMedia(
    val mediaRefId: String,
    val uri: String,
    val mimeType: String,
    val capturedAt: String,
)

@Serializable
data class BackupRating(val stars: Int)

@Serializable
data class BackupTastingNotes(
    val text: String = "",
    val flavorTags: List<String> = emptyList(),
)

/** JSON configuration shared by export and import; defaults are encoded so `schema_version` is always present. */
val MementoBackupJson: Json = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/** Snapshots [mementos] into a versioned backup envelope stamped with [exportedAt]. */
fun backupV1(mementos: List<Memento>, exportedAt: Instant): MementoBackupV1 = MementoBackupV1(
    schemaVersion = MementoBackupV1.SCHEMA_VERSION,
    exportedAt = exportedAt.toString(),
    mementos = mementos.map { it.toBackupMemento() },
)

/** Converts a decoded backup envelope back into domain [Memento]s. */
fun MementoBackupV1.toMementos(): List<Memento> = mementos.map { it.toMemento() }

/** Maps a domain aggregate onto its serializable backup representation. */
fun Memento.toBackupMemento(): BackupMemento = BackupMemento(
    id = id.value,
    title = title,
    reflection = reflection,
    coordinates = coordinates?.let {
        BackupCoordinates(
            latitude = it.latitude,
            longitude = it.longitude,
            accuracyMeters = it.accuracyMeters,
        )
    },
    place = place?.let {
        BackupPlace(
            name = it.name,
            brand = it.brand,
            neighborhood = it.neighborhood,
            city = it.city,
            country = it.country,
        )
    },
    media = media.map {
        BackupMedia(
            mediaRefId = it.id.value,
            uri = it.uri,
            mimeType = it.mimeType,
            capturedAt = it.capturedAt.toString(),
        )
    },
    rating = rating?.let { BackupRating(it.stars) },
    tastingNotes = tastingNotes?.let {
        BackupTastingNotes(text = it.text, flavorTags = it.flavorTags)
    },
    tags = tags.map { it.value },
    collections = collectionIds.map { it.value },
    priceMinorUnits = priceMinorUnits,
    currencyCode = currencyCode,
    occurredAt = occurredAt.toString(),
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)

/** Reconstructs the domain aggregate, letting the domain invariants validate the payload. */
fun BackupMemento.toMemento(): Memento = Memento(
    id = MementoId(id),
    title = title,
    reflection = reflection,
    coordinates = coordinates?.let {
        Coordinates(
            latitude = it.latitude,
            longitude = it.longitude,
            accuracyMeters = it.accuracyMeters,
        )
    },
    place = place?.let {
        Place(
            name = it.name,
            brand = it.brand,
            neighborhood = it.neighborhood,
            city = it.city,
            country = it.country,
        )
    },
    media = media.map {
        MediaReference(
            id = MediaId(it.mediaRefId),
            uri = it.uri,
            mimeType = it.mimeType,
            capturedAt = Instant.parse(it.capturedAt),
        )
    },
    rating = rating?.let { Rating(it.stars) },
    tastingNotes = tastingNotes?.let { TastingNotes(it.text, it.flavorTags) },
    tags = tags.map { Tag(it) },
    collectionIds = collections.map { CollectionId(it) },
    priceMinorUnits = priceMinorUnits,
    currencyCode = currencyCode,
    occurredAt = Instant.parse(occurredAt),
    createdAt = Instant.parse(createdAt),
    updatedAt = Instant.parse(updatedAt),
)
