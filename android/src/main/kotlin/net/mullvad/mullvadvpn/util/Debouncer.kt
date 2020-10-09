package net.mullvad.mullvadvpn.util

import kotlin.properties.Delegates.observable
import kotlinx.coroutines.delay

// An interval of zero means that it will only debounce events that are sent before the job is
// started. If the events are coming from the UI thread, this means that this class will only send
// the last event received before the UI thread finishes its current task.
class Debouncer<T>(initialValue: T, val intervalInMs: Long = 0) {
    private val jobTracker = JobTracker()

    var listener: ((T) -> Unit)? = null

    var debouncedValue = initialValue
        private set

    var rawValue by observable(initialValue) { _, oldValue, newValue ->
        if (newValue != oldValue) {
            jobTracker.cancelJob("notifyNewValue")

            if (newValue != debouncedValue) {
                jobTracker.newUiJob("notifyNewValue") {
                    delay(intervalInMs)
                    listener?.invoke(newValue)
                    debouncedValue = newValue
                }
            }
        }
    }
}
