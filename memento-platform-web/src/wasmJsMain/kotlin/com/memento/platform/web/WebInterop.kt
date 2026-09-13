package com.memento.platform.web

import kotlin.js.JsAny
import kotlin.js.JsName
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set

/**
 * Minimal, typed JavaScript interop used by the browser platform services.
 *
 * Kotlin/Wasm does not support the `dynamic` type or `asDynamic()`, so every JavaScript surface is
 * described with `external` declarations instead.
 */

@JsName("indexedDB")
internal external val indexedDbFactory: WasmIdbFactory

internal external interface WasmIdbFactory : JsAny {
    fun open(name: String, version: Int): WasmIdbOpenDbRequest
}

internal external interface WasmIdbOpenDbRequest : WasmIdbRequest {
    var onupgradeneeded: ((WasmIdbEvent) -> Unit)?
}

internal external interface WasmIdbRequest : JsAny {
    val result: JsAny?
    val error: JsAny?
    var onsuccess: ((WasmIdbEvent) -> Unit)?
    var onerror: ((WasmIdbEvent) -> Unit)?
}

internal external interface WasmIdbEvent : JsAny

internal external interface WasmIdbDatabase : JsAny {
    val objectStoreNames: WasmDomStringList
    fun createObjectStore(name: String): WasmIdbObjectStore
    fun transaction(storeName: String, mode: String): WasmIdbTransaction
}

internal external interface WasmDomStringList : JsAny {
    fun contains(value: String): Boolean
}

internal external interface WasmIdbTransaction : JsAny {
    fun objectStore(name: String): WasmIdbObjectStore
}

internal external interface WasmIdbObjectStore : JsAny {
    fun put(value: JsAny, key: String): WasmIdbRequest
    fun get(key: String): WasmIdbRequest
    fun getAll(): WasmIdbRequest
    fun delete(key: String): WasmIdbRequest
    fun clear(): WasmIdbRequest
}

/**
 * Shape of a persisted JSON payload: `{ json: string }`. The JSON text is converted to a
 * JavaScript string before being embedded so IndexedDB always receives a cloneable value.
 */
internal external interface StoredJsonRecord : JsAny {
    val json: String
}

internal fun createJsonRecord(json: String): JsAny = wrapJson(json.toJsString())

private fun wrapJson(json: kotlin.js.JsString): JsAny = js("({ json: json })")

/**
 * Shape of a persisted media record: `{ id, mimeType, fileName, bytes: Uint8Array }`.
 */
internal external interface StoredMediaRecord : JsAny {
    val id: String
    val mimeType: String
    val fileName: String
    val bytes: Uint8Array
}

@JsName("navigator")
internal external val browserNavigator: WasmNavigator

internal external interface WasmNavigator : JsAny {
    val geolocation: WasmGeolocation
}

internal external interface WasmGeolocation : JsAny {
    fun getCurrentPosition(
        successCallback: (WasmGeolocationPosition) -> Unit,
        errorCallback: (JsAny) -> Unit,
    )
}

internal external interface WasmGeolocationPosition : JsAny {
    val coords: WasmGeolocationCoordinates
}

internal external interface WasmGeolocationCoordinates : JsAny {
    val latitude: Double
    val longitude: Double
    val accuracy: Double
}

/**
 * Builds the plain JavaScript object stored in IndexedDB. The compiler injects the surrounding
 * scope, so the parameter names are visible to the snippet.
 */
internal fun createMediaRecord(
    id: String,
    mimeType: String,
    fileName: String,
    bytes: Uint8Array,
): JsAny = js("({ id: id, mimeType: mimeType, fileName: fileName, bytes: bytes })")

internal fun ByteArray.toWasmUint8Array(): Uint8Array {
    val result = Uint8Array(size)
    for (index in indices) {
        result[index] = this[index]
    }
    return result
}

internal fun Uint8Array.toWasmByteArray(): ByteArray = ByteArray(length) { this[it] }
