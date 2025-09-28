package com.example.tracker

data class Event(
    val name: String,
    val timestamp: Long = System.currentTimeMillis(),
    val properties: Map<String, Any> = emptyMap()
)

interface AnalyticsTracker {
    fun track(event: Event)
    fun track(eventName: String, properties: Map<String, Any> = emptyMap())
    fun getEvents(): List<Event>
}

class Analytics : AnalyticsTracker {
    private val events = mutableListOf<Event>()

    override fun track(event: Event) {
        events.add(event)
        println("[ANALYTICS] Tracked event: ${event.name}")
    }

    override fun track(eventName: String, properties: Map<String, Any>) {
        track(Event(eventName, properties = properties))
    }

    fun trackEvent(eventName: String) {
        track(eventName)
    }

    override fun getEvents(): List<Event> {
        return events.toList()
    }
}