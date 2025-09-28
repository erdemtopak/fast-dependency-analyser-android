package com.example.feature

import com.example.library.DataService
import com.example.library.Logger
import com.example.library.User
import com.example.tracker.Analytics

class HomeController {
    private val dataService = DataService()
    private val analytics = Analytics()

    fun displayHomePage(userId: Long): String {
        Logger.info("Displaying home page for user: $userId")
        analytics.trackEvent("home_page_view")

        val user = dataService.getUser(userId)

        return buildString {
            appendLine("Welcome to the Home Page!")
            appendLine("User: ${user.name}")
            appendLine("Email: ${user.email}")
        }
    }

    fun updateUserProfile(user: User): Boolean {
        Logger.info("Updating user profile for: ${user.name}")
        analytics.trackEvent("user_profile_update")
        return dataService.saveUser(user)
    }
}