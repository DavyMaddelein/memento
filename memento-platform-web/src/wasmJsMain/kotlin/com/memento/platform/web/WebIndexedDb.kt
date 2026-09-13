package com.memento.platform.web

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.JsAny
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Shared handle to the `memento` IndexedDB database.
 *
 * A single database hosts three object stores: `media` (binary records), `mementos` and
 * `collections` (JSON text). [DB_VERSION] must be bumped whenever a store is added so that the
 * `onupgradeneeded` handler can create the missing stores. All stores use out-of-line keys, so the
 * database can be upgraded from the earlier media-only schema without losing data.
 */
internal object MementoIndexedDb {
    const val DB_NAME = "memento"
    const val DB_VERSION = 2
    const val MEDIA_STORE = "media"
    const val MEMENTOS_STORE = "mementos"
    const val COLLECTIONS_STORE = "collections"

    private var database: WasmIdbDatabase? = null

    suspend fun database(): WasmIdbDatabase {
        database?.let { return it }
        return open().also { database = it }
    }

    /** Opens a read-only transaction-scoped object store. */
    suspend fun store(storeName: String): WasmIdbObjectStore =
        database().transaction(storeName, READONLY).objectStore(storeName)

    /** Opens a read-write transaction-scoped object store. */
    suspend fun writeStore(storeName: String): WasmIdbObjectStore =
        database().transaction(storeName, READWRITE).objectStore(storeName)

    private const val READONLY = "readonly"
    private const val READWRITE = "readwrite"

    private suspend fun open(): WasmIdbDatabase = suspendCancellableCoroutine { continuation ->
        val request = indexedDbFactory.open(DB_NAME, DB_VERSION)
        request.onupgradeneeded = {
            val result = request.result
            if (result != null) {
                val db = result as WasmIdbDatabase
                db.ensureStore(MEDIA_STORE)
                db.ensureStore(MEMENTOS_STORE)
                db.ensureStore(COLLECTIONS_STORE)
            }
        }
        request.onsuccess = {
            val result = request.result
            if (result == null) {
                if (continuation.isActive) {
                    continuation.resumeWithException(
                        IllegalStateException("IndexedDB opened '$DB_NAME' without a database handle"),
                    )
                }
            } else if (continuation.isActive) {
                continuation.resume(result as WasmIdbDatabase)
            }
        }
        request.onerror = {
            if (continuation.isActive) {
                continuation.resumeWithException(
                    IllegalStateException("Failed to open IndexedDB '$DB_NAME': ${request.error}"),
                )
            }
        }
    }
}

private fun WasmIdbDatabase.ensureStore(name: String) {
    if (!objectStoreNames.contains(name)) {
        createObjectStore(name)
    }
}

/**
 * Awaits an IndexedDB request, resuming with its result. The request's `onsuccess`/`onerror`
 * handlers are overwritten, so callers must issue and await one request at a time.
 */
internal suspend fun WasmIdbRequest.awaitResult(): JsAny? =
    suspendCancellableCoroutine { continuation ->
        onsuccess = {
            if (continuation.isActive) {
                continuation.resume(result)
            }
        }
        onerror = {
            if (continuation.isActive) {
                continuation.resumeWithException(
                    IllegalStateException("IndexedDB request failed: $error"),
                )
            }
        }
    }
