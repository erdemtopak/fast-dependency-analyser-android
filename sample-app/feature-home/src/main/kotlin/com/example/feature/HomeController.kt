package com.example.feature

import com.example.library.DataService
import com.example.library.Logger
import com.example.library.User

class HomeController {
    private val dataService = DataService()

    fun displayHomePage(userId: Long): String {
        Logger.info("Displaying home page for user: $userId")

        val user = dataService.getUser(userId)

        return buildString {
            appendLine("Welcome to the Home Page!")
            appendLine("User: ${user.name}")
            appendLine("Email: ${user.email}")
        }
    }

    fun updateUserProfile(user: User): Boolean {
        Logger.info("Updating user profile for: ${user.name}")
        return dataService.saveUser(user)
    }
}