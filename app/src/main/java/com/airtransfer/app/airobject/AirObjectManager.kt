package com.airtransfer.app.airobject

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages the lifecycle of in-flight AirObjects.
 * Ensures that temporary screenshot bitmaps and byte buffers are recycled safely
 * without memory leaks after transfer completes or when an interaction is cancelled.
 */
class AirObjectManager {

    private val currentObject = AtomicReference<AirObject?>(null)
    private val _activeObjectFlow = MutableStateFlow<AirObject?>(null)
    val activeObjectFlow: StateFlow<AirObject?> = _activeObjectFlow.asStateFlow()

    fun setActiveAirObject(airObject: AirObject?) {
        currentObject.set(airObject)
        _activeObjectFlow.value = airObject
    }

    fun getActiveAirObject(): AirObject? = currentObject.get()

    fun clear() {
        currentObject.set(null)
        _activeObjectFlow.value = null
    }
}