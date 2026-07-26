package com.yahigod.homestashtv

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.yahigod.homestashtv.ui.theme.HomeStashTvTheme
import org.junit.Rule
import org.junit.Test

class ReceiverHomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun firstLaunchIsReadableAndFocusesExit() {
        composeRule.setContent {
            HomeStashTvTheme {
                ReceiverHomeScreen(onExit = {})
            }
        }

        composeRule.onNodeWithText("Receiver ready").assertIsDisplayed()
        composeRule
            .onNodeWithText("Waiting for a Home Stash connection")
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Exit app, focused")
            .assertIsDisplayed()
    }
}
