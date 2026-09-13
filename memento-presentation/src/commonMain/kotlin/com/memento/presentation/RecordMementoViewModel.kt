package com.memento.presentation

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
import com.memento.domain.util.randomId
import com.memento.domain.validation.MementoValidator
import com.memento.domain.validation.ValidationViolation
import com.memento.platform.contract.LocationProvider
import com.memento.platform.contract.PhotoPickerService
import com.memento.platform.contract.ResolvedPlace
import com.memento.platform.contract.ReverseGeocodingService
import com.memento.storage.contract.AssetStore
import com.memento.storage.contract.MementoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Immutable form state for creating or editing a keepsake.
 */
data class RecordMementoUiState(
    val mementoId: MementoId? = null,
    val media: List<MediaReference> = emptyList(),
    val coordinates: Coordinates? = null,
    val isFetchingLocation: Boolean = false,
    val placeName: String = "",
    val brand: String = "",
    val city: String = "",
    val title: String = "",
    val rating: Int = 0,
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val collectionIds: List<CollectionId> = emptyList(),
    val errors: List<ValidationViolation> = emptyList(),
    val isSaving: Boolean = false,
    val savedMementoId: MementoId? = null,
    val isResolvingPlace: Boolean = false,
    val placeLookupFailed: Boolean = false,
    val occurredAt: Instant = Clock.System.now(),
    val priceText: String = "",
    val currencyCode: String = "JPY",
)

/** Convenience-store chains recognised in a reverse-geocoded place name. */
private val KNOWN_BRANDS = listOf("7-Eleven", "Lawson", "FamilyMart", "MiniStop", "Daily Yamazaki")

/**
 * MVI ViewModel driving the "record a memento" form: media capture, location acquisition, place
 * metadata, rating, notes, tags, collection membership and validation-aware persistence.
 */
class RecordMementoViewModel(
    private val mementoRepository: MementoRepository,
    private val assetStore: AssetStore? = null,
    private val locationProvider: LocationProvider,
    private val photoPicker: PhotoPickerService,
    private val reverseGeocodingService: ReverseGeocodingService? = null,
    private val scope: CoroutineScope = defaultViewModelScope(),
) {
    private val _state = MutableStateFlow(RecordMementoUiState())
    val state: StateFlow<RecordMementoUiState> = _state.asStateFlow()

    fun onAddFromCamera() {
        scope.launch {
            photoPicker.launchCamera().fold(
                onSuccess = { reference ->
                    _state.value = _state.value.copy(
                        media = _state.value.media + reference,
                        errors = _state.value.errors.filterNot { it.field == "media" },
                    )
                },
                onFailure = { error ->
                    addError(ValidationViolation("media", error.message ?: "Unable to capture photo"))
                },
            )
        }
    }

    fun onAddFromGallery() {
        scope.launch {
            photoPicker.launchGallery().fold(
                onSuccess = { references ->
                    _state.value = _state.value.copy(
                        media = _state.value.media + references,
                        errors = _state.value.errors.filterNot { it.field == "media" },
                    )
                },
                onFailure = { error ->
                    addError(ValidationViolation("media", error.message ?: "Unable to select photos"))
                },
            )
        }
    }

    fun onRemovePhoto(mediaId: MediaId) {
        _state.value = _state.value.copy(media = _state.value.media.filterNot { it.id == mediaId })
        assetStore?.let { store ->
            scope.launch { store.deleteMedia(mediaId) }
        }
    }

    fun onFetchLocationClicked() {
        _state.value = _state.value.copy(
            isFetchingLocation = true,
            errors = _state.value.errors.filterNot { it.field == "location" },
        )
        scope.launch {
            locationProvider.getCurrentCoordinates().fold(
                onSuccess = { coordinates ->
                    _state.value = _state.value.copy(
                        coordinates = coordinates,
                        isFetchingLocation = false,
                        placeLookupFailed = false,
                    )
                    resolvePlace(coordinates)
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        isFetchingLocation = false,
                        errors = _state.value.errors + ValidationViolation(
                            "location",
                            error.message ?: "Unable to determine location",
                        ),
                    )
                },
            )
        }
    }

    /**
     * Optional second step: turn the coordinates into place details via a free geocoding service.
     * Best-effort by design — it only fills blank fields and, when it fails (offline, timeout,
     * rate limited), it just flags [RecordMementoUiState.placeLookupFailed]. Coordinates are never
     * discarded, so the keep can still be saved by typing the place manually.
     */
    private fun resolvePlace(coordinates: Coordinates) {
        val service = reverseGeocodingService ?: return
        _state.value = _state.value.copy(isResolvingPlace = true, placeLookupFailed = false)
        scope.launch {
            service.reverseGeocode(coordinates).fold(
                onSuccess = { place ->
                    _state.value = _state.value.copy(
                        isResolvingPlace = false,
                        placeName = _state.value.placeName.ifBlank {
                            place.name ?: place.neighborhood ?: place.city ?: ""
                        },
                        city = _state.value.city.ifBlank { place.city ?: "" },
                        brand = _state.value.brand.ifBlank { knownBrandIn(place) ?: "" },
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        isResolvingPlace = false,
                        placeLookupFailed = true,
                    )
                },
            )
        }
    }

    private fun knownBrandIn(place: ResolvedPlace): String? {
        val haystack = listOfNotNull(place.name, place.displayName).joinToString(" ").lowercase()
        if (haystack.isEmpty()) return null
        return KNOWN_BRANDS.firstOrNull { haystack.contains(it.lowercase()) }
    }

    fun onPlaceNameChanged(value: String) {
        _state.value = _state.value.copy(placeName = value, placeLookupFailed = false)
    }

    fun onBrandChanged(value: String) {
        _state.value = _state.value.copy(brand = value, placeLookupFailed = false)
    }

    fun onCityChanged(value: String) {
        _state.value = _state.value.copy(city = value, placeLookupFailed = false)
    }

    fun onTitleChanged(value: String) {
        _state.value = _state.value.copy(title = value)
    }

    fun onRatingChanged(value: Int) {
        _state.value = _state.value.copy(rating = value.coerceIn(0, Rating.MAX))
    }

    fun onNotesChanged(value: String) {
        _state.value = _state.value.copy(notes = value)
    }

    fun onTagAdded(tag: String) {
        val trimmed = tag.trim()
        when {
            trimmed.isEmpty() -> return
            trimmed.length > Tag.MAX_LENGTH -> {
                addError(
                    ValidationViolation("tags", "Tag must be at most ${Tag.MAX_LENGTH} characters"),
                )
            }
            _state.value.tags.any { it.equals(trimmed, ignoreCase = true) } -> return
            else -> _state.value = _state.value.copy(tags = _state.value.tags + trimmed)
        }
    }

    fun onTagRemoved(tag: String) {
        _state.value = _state.value.copy(
            tags = _state.value.tags.filterNot { it.equals(tag, ignoreCase = true) },
        )
    }

    fun onCollectionToggled(collectionId: CollectionId) {
        val current = _state.value.collectionIds
        _state.value = _state.value.copy(
            collectionIds = if (collectionId in current) current - collectionId else current + collectionId,
        )
    }

    fun onOccurredAtChanged(value: Instant) {
        _state.value = _state.value.copy(occurredAt = value)
    }

    fun onPriceChanged(value: String) {
        _state.value = _state.value.copy(
            priceText = value,
            errors = _state.value.errors.filterNot { it.field == "price" },
        )
    }

    fun onCurrencyChanged(value: String) {
        _state.value = _state.value.copy(
            currencyCode = value.uppercase().take(3),
            errors = _state.value.errors.filterNot { it.field == "currencyCode" },
        )
    }

    fun onSaveClicked() {
        // Ignore repeat taps: while a save is in flight or after this form has already
        // produced a memento, a second tap must not create a duplicate entry.
        if (_state.value.isSaving || _state.value.savedMementoId != null) return
        val memento = buildMemento()
        val violations = validateRawInputs() + MementoValidator.validate(memento)
        if (violations.isNotEmpty()) {
            _state.value = _state.value.copy(errors = violations)
            return
        }
        _state.value = _state.value.copy(errors = emptyList(), isSaving = true)
        scope.launch {
            mementoRepository.saveMemento(memento).fold(
                onSuccess = {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        savedMementoId = memento.id,
                    )
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        isSaving = false,
                        errors = listOf(
                            ValidationViolation("save", error.message ?: "Unable to save the memento"),
                        ),
                    )
                },
            )
        }
    }

    /** Clears the form so the next capture starts from a blank keepsake. */
    fun reset() {
        _state.value = RecordMementoUiState()
    }

    fun loadForEdit(memento: Memento) {
        _state.value = RecordMementoUiState(
            mementoId = memento.id,
            media = memento.media,
            coordinates = memento.coordinates,
            placeName = memento.place?.name.orEmpty(),
            brand = memento.place?.brand.orEmpty(),
            city = memento.place?.city.orEmpty(),
            title = memento.title,
            rating = memento.rating?.stars ?: 0,
            notes = memento.tastingNotes?.text ?: memento.reflection,
            tags = memento.tags.map { it.value },
            collectionIds = memento.collectionIds,
            occurredAt = memento.occurredAt,
            priceText = memento.priceMinorUnits?.toString() ?: "",
            currencyCode = memento.currencyCode ?: "JPY",
        )
    }

    fun dispose() {
        scope.cancel()
    }

    private fun addError(violation: ValidationViolation) {
        _state.value = _state.value.copy(errors = _state.value.errors + violation)
    }

    /**
     * Validates the raw, unparsed text fields. [buildMemento] intentionally drops malformed price
     * or currency input to `null`, so the aggregate [MementoValidator] cannot see it; these checks
     * surface the problem to the form instead.
     */
    private fun validateRawInputs(): List<ValidationViolation> = buildList {
        val price = _state.value.priceText.trim()
        if (price.isNotEmpty()) {
            val parsed = price.toLongOrNull()
            if (parsed == null || parsed < 0) {
                add(ValidationViolation(field = "price", message = "Enter a valid non-negative price"))
            }
        }
        val currency = _state.value.currencyCode.trim()
        if (currency.length != 3 || !currency.all { it.isLetter() }) {
            add(
                ValidationViolation(
                    field = "currencyCode",
                    message = "currencyCode must be a 3-letter ISO code",
                ),
            )
        }
    }

    private fun buildMemento(): Memento {
        val now = Clock.System.now()
        val current = _state.value
        val id = current.mementoId ?: MementoId(randomId("memento"))
        return Memento(
            id = id,
            title = current.title.trim(),
            reflection = "",
            coordinates = current.coordinates,
            place = current.placeName.trim().takeIf { it.isNotEmpty() }?.let { name ->
                Place(
                    name = name,
                    brand = current.brand.trim().takeIf { it.isNotEmpty() },
                    city = current.city.trim().takeIf { it.isNotEmpty() },
                )
            },
            media = current.media,
            rating = current.rating.takeIf { it in Rating.MIN..Rating.MAX }?.let { Rating(it) },
            tastingNotes = current.notes.trim().takeIf { it.isNotEmpty() }?.let {
                TastingNotes(text = it)
            },
            tags = current.tags
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.length <= Tag.MAX_LENGTH }
                .distinctBy { it.lowercase() }
                .map { Tag(it) },
            collectionIds = current.collectionIds,
            priceMinorUnits = current.priceText.trim().takeIf { it.isNotEmpty() }?.toLongOrNull(),
            currencyCode = current.currencyCode.trim().uppercase().takeIf { it.length == 3 },
            occurredAt = current.occurredAt,
            createdAt = now,
            updatedAt = now,
        )
    }
}
