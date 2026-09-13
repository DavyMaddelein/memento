@file:OptIn(ExperimentalCoroutinesApi::class)

package com.memento.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.memento.domain.model.Coordinates
import com.memento.domain.model.MediaId
import com.memento.platform.contract.ResolvedPlace
import com.memento.platform.contract.fakes.FakeLocationProvider
import com.memento.platform.contract.fakes.FakePhotoPickerService
import com.memento.platform.contract.fakes.FakeReverseGeocodingService
import com.memento.storage.memory.InMemoryAssetStore
import com.memento.storage.memory.InMemoryMementoRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordMementoViewModelTest {
    @Test
    fun cameraIntentAppendsMedia() = runTest {
        val repository = InMemoryMementoRepository()
        val picker = FakePhotoPickerService()
        val viewModel = RecordMementoViewModel(
            mementoRepository = repository,
            assetStore = InMemoryAssetStore(),
            locationProvider = FakeLocationProvider(),
            photoPicker = picker,
            scope = newViewModelScope(),
        )

        viewModel.onAddFromCamera()
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.media.size)
        assertEquals(1, picker.cameraCalls)

        viewModel.dispose()
        advanceUntilIdle()
    }

    @Test
    fun galleryIntentAppendsAndRemoveRemovesMedia() = runTest {
        val repository = InMemoryMementoRepository()
        val picker = FakePhotoPickerService()
        val viewModel = RecordMementoViewModel(
            mementoRepository = repository,
            locationProvider = FakeLocationProvider(),
            photoPicker = picker,
            scope = newViewModelScope(),
        )

        viewModel.onAddFromGallery()
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.media.size)
        assertEquals(1, picker.galleryCalls)

        val mediaId: MediaId = viewModel.state.value.media.first().id
        viewModel.onRemovePhoto(mediaId)
        assertTrue(viewModel.state.value.media.isEmpty())

        viewModel.dispose()
        advanceUntilIdle()
    }

    @Test
    fun fetchLocationSetsCoordinates() = runTest {
        val repository = InMemoryMementoRepository()
        val locationProvider = FakeLocationProvider()
        val viewModel = RecordMementoViewModel(
            mementoRepository = repository,
            locationProvider = locationProvider,
            photoPicker = FakePhotoPickerService(),
            scope = newViewModelScope(),
        )

        viewModel.onFetchLocationClicked()
        advanceUntilIdle()

        val expected: Coordinates? = locationProvider.coordinates
        assertEquals(expected, viewModel.state.value.coordinates)
        assertFalse(viewModel.state.value.isFetchingLocation)
        assertEquals(1, locationProvider.callCount)

        viewModel.dispose()
        advanceUntilIdle()
    }

    @Test
    fun fetchLocationFailureSurfacesError() = runTest {
        val repository = InMemoryMementoRepository()
        val locationProvider = FakeLocationProvider(permissionGranted = false)
        val viewModel = RecordMementoViewModel(
            mementoRepository = repository,
            locationProvider = locationProvider,
            photoPicker = FakePhotoPickerService(),
            scope = newViewModelScope(),
        )

        viewModel.onFetchLocationClicked()
        advanceUntilIdle()

        assertNull(viewModel.state.value.coordinates)
        assertFalse(viewModel.state.value.isFetchingLocation)
        assertTrue(viewModel.state.value.errors.any { it.field == "location" })

        viewModel.dispose()
        advanceUntilIdle()
    }

    @Test
    fun validationBlocksSaveWithoutMediaAndTitle() = runTest {
        val repository = InMemoryMementoRepository()
        val viewModel = RecordMementoViewModel(
            mementoRepository = repository,
            locationProvider = FakeLocationProvider(),
            photoPicker = FakePhotoPickerService(),
            scope = newViewModelScope(),
        )

        viewModel.onSaveClicked()
        advanceUntilIdle()

        val errors = viewModel.state.value.errors
        assertTrue(errors.any { it.field == "media" })
        assertTrue(errors.any { it.field == "title" })
        assertNull(viewModel.state.value.savedMementoId)
        assertTrue(repository.observeAllMementos().first().isEmpty())

        viewModel.dispose()
        advanceUntilIdle()
    }

    @Test
    fun successfulSavePersistsMemento() = runTest {
        val repository = InMemoryMementoRepository()
        val viewModel = RecordMementoViewModel(
            mementoRepository = repository,
            locationProvider = FakeLocationProvider(),
            photoPicker = FakePhotoPickerService(),
            scope = newViewModelScope(),
        )

        viewModel.onAddFromCamera()
        viewModel.onTitleChanged("Tokyo Konbini")
        viewModel.onTagAdded("coffee")
        viewModel.onRatingChanged(4)
        advanceUntilIdle()

        viewModel.onSaveClicked()
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.savedMementoId)
        assertTrue(viewModel.state.value.errors.isEmpty())
        assertFalse(viewModel.state.value.isSaving)

        val persisted = repository.observeAllMementos().first()
        assertEquals(1, persisted.size)
        assertEquals("Tokyo Konbini", persisted.single().title)
        assertEquals(4, persisted.single().rating?.stars)
        assertEquals(listOf("coffee"), persisted.single().tags.map { it.value })

        viewModel.dispose()
        advanceUntilIdle()
    }

    @Test
    fun repeatedSaveClicksPersistOnlyOneMemento() = runTest {
        val repository = InMemoryMementoRepository()
        val viewModel = RecordMementoViewModel(
            mementoRepository = repository,
            locationProvider = FakeLocationProvider(),
            photoPicker = FakePhotoPickerService(),
            scope = newViewModelScope(),
        )

        viewModel.onAddFromCamera()
        viewModel.onTitleChanged("Tokyo Konbini")
        advanceUntilIdle()

        // Three taps in a row must not create duplicates.
        viewModel.onSaveClicked()
        viewModel.onSaveClicked()
        viewModel.onSaveClicked()
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.savedMementoId)
        assertEquals(1, repository.observeAllMementos().first().size)

        // Tapping again after the save completed is still a no-op.
        viewModel.onSaveClicked()
        advanceUntilIdle()
        assertEquals(1, repository.observeAllMementos().first().size)

        // Resetting the form starts a new keepsake.
        viewModel.reset()
        assertNull(viewModel.state.value.savedMementoId)

        viewModel.dispose()
        advanceUntilIdle()
    }

    @Test
    fun loadForEditPopulatesStateAndSavesSameId() = runTest {
        val repository = InMemoryMementoRepository()
        val existing = testMemento(id = "existing", title = "Old Title")
        repository.saveMemento(existing)
        val viewModel = RecordMementoViewModel(
            mementoRepository = repository,
            locationProvider = FakeLocationProvider(),
            photoPicker = FakePhotoPickerService(),
            scope = newViewModelScope(),
        )

        viewModel.loadForEdit(existing)
        assertEquals("existing", viewModel.state.value.mementoId?.value)
        assertEquals("Old Title", viewModel.state.value.title)

        viewModel.onTitleChanged("New Title")
        viewModel.onSaveClicked()
        advanceUntilIdle()

        assertEquals("existing", viewModel.state.value.savedMementoId?.value)
        val persisted = repository.observeAllMementos().first()
        assertEquals(1, persisted.size)
        assertEquals("New Title", persisted.single().title)

        viewModel.dispose()
        advanceUntilIdle()
    }

    @Test
    fun geocodingFillsBlankPlaceFields() = runTest {
        val geocoder = FakeReverseGeocodingService(
            result = Result.success(
                ResolvedPlace(
                    name = "Shibuya Station",
                    neighborhood = "Dogenzaka",
                    city = "Shibuya",
                    country = "Japan",
                    displayName = "Shibuya Station, Shibuya, Tokyo, Japan",
                ),
            ),
        )
        val viewModel = RecordMementoViewModel(
            mementoRepository = InMemoryMementoRepository(),
            locationProvider = FakeLocationProvider(),
            photoPicker = FakePhotoPickerService(),
            reverseGeocodingService = geocoder,
            scope = newViewModelScope(),
        )

        viewModel.onFetchLocationClicked()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state.coordinates)
        assertEquals("Shibuya Station", state.placeName)
        assertEquals("Shibuya", state.city)
        assertFalse(state.isResolvingPlace)
        assertFalse(state.placeLookupFailed)
        assertEquals(1, geocoder.callCount)

        viewModel.dispose()
        advanceUntilIdle()
    }

    @Test
    fun geocodingDoesNotOverwriteTypedFields() = runTest {
        val geocoder = FakeReverseGeocodingService(
            result = Result.success(ResolvedPlace(name = "Shibuya Station", city = "Shibuya")),
        )
        val viewModel = RecordMementoViewModel(
            mementoRepository = InMemoryMementoRepository(),
            locationProvider = FakeLocationProvider(),
            photoPicker = FakePhotoPickerService(),
            reverseGeocodingService = geocoder,
            scope = newViewModelScope(),
        )

        viewModel.onPlaceNameChanged("My Place")
        viewModel.onCityChanged("My City")
        viewModel.onBrandChanged("My Brand")
        viewModel.onFetchLocationClicked()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("My Place", state.placeName)
        assertEquals("My City", state.city)
        assertEquals("My Brand", state.brand)

        viewModel.dispose()
        advanceUntilIdle()
    }

    @Test
    fun geocodingFailureKeepsCoordinatesAndFlagsSoftFailure() = runTest {
        val geocoder = FakeReverseGeocodingService(
            result = Result.failure(IllegalStateException("offline")),
        )
        val viewModel = RecordMementoViewModel(
            mementoRepository = InMemoryMementoRepository(),
            locationProvider = FakeLocationProvider(),
            photoPicker = FakePhotoPickerService(),
            reverseGeocodingService = geocoder,
            scope = newViewModelScope(),
        )

        viewModel.onFetchLocationClicked()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state.coordinates)
        assertTrue(state.placeLookupFailed)
        assertFalse(state.isResolvingPlace)
        assertTrue(state.errors.none { it.field == "location" })

        viewModel.dispose()
        advanceUntilIdle()
    }

    @Test
    fun noGeocodingServiceLeavesNoLookupFailure() = runTest {
        val viewModel = RecordMementoViewModel(
            mementoRepository = InMemoryMementoRepository(),
            locationProvider = FakeLocationProvider(),
            photoPicker = FakePhotoPickerService(),
            scope = newViewModelScope(),
        )

        viewModel.onFetchLocationClicked()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state.coordinates)
        assertFalse(state.placeLookupFailed)
        assertFalse(state.isResolvingPlace)

        viewModel.dispose()
        advanceUntilIdle()
    }
}
