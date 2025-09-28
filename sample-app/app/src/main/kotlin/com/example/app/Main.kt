package com.example.app

import com.example.feature.HomeController
import com.example.library.Logger
import com.example.library.User

fun main() {
    Logger.info("Starting Sample Application")

    val homeController = HomeController()

    // Display home page
    val homePage = homeController.displayHomePage(userId = 1L)
    println(homePage)

    // Update user profile
    val user = User(1L, "John Doe", "john@example.com")
    val updateResult = homeController.updateUserProfile(user)

    Logger.info("User profile update result: $updateResult")
    Logger.info("Application finished")
}