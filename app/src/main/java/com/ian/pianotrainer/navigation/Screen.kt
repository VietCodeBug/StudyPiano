package com.ian.pianotrainer.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Piano
import androidx.compose.ui.graphics.vector.ImageVector
import com.ian.pianotrainer.R

sealed class Screen(val route: String) {
    // 5 Primary Bottom Navigation Destinations
    object Home : Screen("home")
    object Learn : Screen("learn")
    object Practice : Screen("practice")
    object MySongs : Screen("my_songs")
    object Progress : Screen("progress")

    // Secondary Destinations
    object CourseDetail : Screen("course_detail/{courseId}") {
        fun createRoute(courseId: String): String {
            val encodedId = Uri.encode(courseId)
            return "course_detail/$encodedId"
        }
    }

    object LessonDetail : Screen("lesson_detail/{lessonId}") {
        fun createRoute(lessonId: String): String {
            val encodedId = Uri.encode(lessonId)
            return "lesson_detail/$encodedId"
        }
    }

    object PracticePlayer : Screen("practice_player?title={title}&sourceType={sourceType}&sourceId={sourceId}&handMode={handMode}&practiceMode={practiceMode}&displayMode={displayMode}&bpm={bpm}") {
        fun createRoute(
            title: String,
            sourceType: String,
            sourceId: String = "",
            handMode: String = "RIGHT",
            practiceMode: String = "RHYTHM",
            displayMode: String = "FALLING_NOTES",
            bpm: Int = 60
        ): String {
            val encTitle = Uri.encode(title)
            val encType = Uri.encode(sourceType)
            val encId = Uri.encode(sourceId)
            val encHand = Uri.encode(handMode)
            val encMode = Uri.encode(practiceMode)
            val encDisplay = Uri.encode(displayMode)
            return "practice_player?title=$encTitle&sourceType=$encType&sourceId=$encId&handMode=$encHand&practiceMode=$encMode&displayMode=$encDisplay&bpm=$bpm"
        }
    }

    object FreePlay : Screen("free_play")
    object MidiDiagnostic : Screen("midi_diagnostic")
    object DeviceConnection : Screen("device_connection")
    object PracticeResult : Screen("practice_result/{sessionId}") {
        fun createRoute(sessionId: String): String {
            val encId = Uri.encode(sessionId)
            return "practice_result/$encId"
        }
    }
    object Settings : Screen("settings")
}

data class BottomNavItem(
    val screen: Screen,
    val titleRes: Int,
    val icon: ImageVector,
    val testTag: String
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, R.string.nav_home, Icons.Default.Home, "nav_home"),
    BottomNavItem(Screen.Learn, R.string.nav_learn, Icons.Default.MenuBook, "nav_learn"),
    BottomNavItem(Screen.Practice, R.string.nav_practice, Icons.Default.Piano, "nav_practice"),
    BottomNavItem(Screen.MySongs, R.string.nav_my_songs, Icons.Default.LibraryMusic, "nav_my_songs"),
    BottomNavItem(Screen.Progress, R.string.nav_progress, Icons.Default.BarChart, "nav_progress")
)
