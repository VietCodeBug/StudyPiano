package com.ian.pianotrainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ian.pianotrainer.app.PianoTrainerApplication
import com.ian.pianotrainer.core.designsystem.PianoBackground
import com.ian.pianotrainer.core.designsystem.PianoTrainerTheme
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.feature.device.DeviceConnectionScreen
import com.ian.pianotrainer.feature.device.DeviceViewModel
import com.ian.pianotrainer.feature.diagnostics.MidiDiagnosticScreen
import com.ian.pianotrainer.feature.diagnostics.MidiDiagnosticViewModel
import com.ian.pianotrainer.feature.freeplay.FreePlayScreen
import com.ian.pianotrainer.feature.freeplay.FreePlayViewModel
import com.ian.pianotrainer.feature.home.HomeScreen
import com.ian.pianotrainer.feature.home.HomeViewModel
import com.ian.pianotrainer.feature.learn.CourseDetailScreen
import com.ian.pianotrainer.feature.learn.CourseDetailViewModel
import com.ian.pianotrainer.feature.learn.LearnScreen
import com.ian.pianotrainer.feature.learn.LearnViewModel
import com.ian.pianotrainer.feature.learn.LessonDetailScreen
import com.ian.pianotrainer.feature.learn.LessonDetailViewModel
import com.ian.pianotrainer.feature.mysongs.MySongsScreen
import com.ian.pianotrainer.feature.mysongs.MySongsViewModel
import com.ian.pianotrainer.feature.practice.PracticePlayerScreen
import com.ian.pianotrainer.feature.practice.PracticePlayerViewModel
import com.ian.pianotrainer.feature.practice.PracticeResultScreen
import com.ian.pianotrainer.feature.practice.PracticeResultViewModel
import com.ian.pianotrainer.feature.practice.PracticeScreen
import com.ian.pianotrainer.feature.practice.PracticeViewModel
import com.ian.pianotrainer.feature.progress.ProgressScreen
import com.ian.pianotrainer.feature.progress.ProgressViewModel
import com.ian.pianotrainer.feature.settings.SettingsScreen
import com.ian.pianotrainer.feature.settings.SettingsViewModel
import com.ian.pianotrainer.navigation.BottomNavBar
import com.ian.pianotrainer.navigation.Screen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as PianoTrainerApplication).container

        setContent {
            PianoTrainerTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val topLevelRoutes = listOf(
                    Screen.Home.route,
                    Screen.Learn.route,
                    Screen.Practice.route,
                    Screen.MySongs.route,
                    Screen.Progress.route
                )

                val showBottomBar = currentRoute in topLevelRoutes

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = PianoBackground,
                    bottomBar = {
                        if (showBottomBar) {
                            BottomNavBar(
                                currentRoute = currentRoute,
                                onNavigateToRoute = { route ->
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // 1. Home
                        composable(Screen.Home.route) {
                            val viewModel: HomeViewModel = viewModel(
                                factory = HomeViewModel.Factory(
                                    curriculumRepository = appContainer.curriculumRepository,
                                    progressRepository = appContainer.progressRepository,
                                    deviceManager = appContainer.pianoDeviceManager
                                )
                            )
                            HomeScreen(
                                viewModel = viewModel,
                                onNavigateToLesson = { lessonId ->
                                    navController.navigate(Screen.LessonDetail.createRoute(lessonId))
                                },
                                onNavigateToFreePlay = {
                                    navController.navigate(Screen.FreePlay.route)
                                },
                                onNavigateToPractice = {
                                    navController.navigate(Screen.Practice.route)
                                },
                                onNavigateToDiagnostics = {
                                    navController.navigate(Screen.MidiDiagnostic.route)
                                },
                                onNavigateToDevice = {
                                    navController.navigate(Screen.DeviceConnection.route)
                                },
                                onNavigateToSettings = {
                                    navController.navigate(Screen.Settings.route)
                                }
                            )
                        }

                        // 2. Learn
                        composable(Screen.Learn.route) {
                            val viewModel: LearnViewModel = viewModel(
                                factory = LearnViewModel.Factory(
                                    curriculumRepository = appContainer.curriculumRepository
                                )
                            )
                            LearnScreen(
                                viewModel = viewModel,
                                onCourseClick = { courseId ->
                                    navController.navigate(Screen.CourseDetail.createRoute(courseId))
                                }
                            )
                        }

                        // 3. Practice Hub
                        composable(Screen.Practice.route) {
                            val viewModel: PracticeViewModel = viewModel(
                                factory = PracticeViewModel.Factory(
                                    settingsRepository = appContainer.settingsRepository,
                                    exerciseRepository = appContainer.exerciseRepository
                                )
                            )
                            PracticeScreen(
                                viewModel = viewModel,
                                onStartPractice = { title, sourceType, sourceId, handMode, displayMode, bpm ->
                                    navController.navigate(
                                        Screen.PracticePlayer.createRoute(
                                            title = title,
                                            sourceType = sourceType,
                                            sourceId = sourceId,
                                            handMode = handMode,
                                            displayMode = displayMode,
                                            bpm = bpm
                                        )
                                    )
                                }
                            )
                        }

                        // 4. My Songs Library
                        composable(Screen.MySongs.route) {
                            val viewModel: MySongsViewModel = viewModel(
                                factory = MySongsViewModel.Factory(
                                    songRepository = appContainer.songRepository
                                )
                            )
                            MySongsScreen(
                                viewModel = viewModel,
                                onPracticeSong = { title, songId, bpm ->
                                    navController.navigate(
                                        Screen.PracticePlayer.createRoute(
                                            title = title,
                                            sourceType = "SONG",
                                            sourceId = songId,
                                            bpm = bpm
                                        )
                                    )
                                }
                            )
                        }

                        // 5. Progress Tracking
                        composable(Screen.Progress.route) {
                            val viewModel: ProgressViewModel = viewModel(
                                factory = ProgressViewModel.Factory(
                                    progressRepository = appContainer.progressRepository
                                )
                            )
                            ProgressScreen(
                                viewModel = viewModel,
                                onSessionClick = { sessionId ->
                                    navController.navigate(Screen.PracticeResult.createRoute(sessionId))
                                }
                            )
                        }

                        // Secondary: Course Detail
                        composable(
                            route = Screen.CourseDetail.route,
                            arguments = listOf(navArgument("courseId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
                            val viewModel: CourseDetailViewModel = viewModel(
                                factory = CourseDetailViewModel.Factory(
                                    courseId = courseId,
                                    curriculumRepository = appContainer.curriculumRepository
                                )
                            )
                            CourseDetailScreen(
                                viewModel = viewModel,
                                onBackClick = { navController.popBackStack() },
                                onLessonSelected = { lessonId ->
                                    navController.navigate(Screen.LessonDetail.createRoute(lessonId))
                                }
                            )
                        }

                        // Secondary: Lesson Detail
                        composable(
                            route = Screen.LessonDetail.route,
                            arguments = listOf(navArgument("lessonId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val lessonId = backStackEntry.arguments?.getString("lessonId") ?: ""
                            val viewModel: LessonDetailViewModel = viewModel(
                                factory = LessonDetailViewModel.Factory(
                                    lessonId = lessonId,
                                    curriculumRepository = appContainer.curriculumRepository,
                                    settingsRepository = appContainer.settingsRepository
                                )
                            )
                            LessonDetailScreen(
                                viewModel = viewModel,
                                onBackClick = { navController.popBackStack() },
                                onStartPractice = { lessonTitle, sourceId, handMode, bpm ->
                                    navController.navigate(
                                        Screen.PracticePlayer.createRoute(
                                            title = lessonTitle,
                                            sourceType = "LESSON",
                                            sourceId = sourceId,
                                            handMode = handMode,
                                            bpm = bpm
                                        )
                                    )
                                }
                            )
                        }

                        // Secondary: Practice Player
                        composable(
                            route = Screen.PracticePlayer.route,
                            arguments = listOf(
                                navArgument("title") {
                                    type = NavType.StringType
                                    defaultValue = "Luyện tập"
                                },
                                navArgument("sourceType") {
                                    type = NavType.StringType
                                    defaultValue = "DRILL"
                                },
                                navArgument("sourceId") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                                navArgument("handMode") {
                                    type = NavType.StringType
                                    defaultValue = "RIGHT"
                                },
                                navArgument("displayMode") {
                                    type = NavType.StringType
                                    defaultValue = "FALLING_NOTES"
                                },
                                navArgument("bpm") {
                                    type = NavType.IntType
                                    defaultValue = 60
                                }
                            )
                        ) { backStackEntry ->
                            val title = backStackEntry.arguments?.getString("title") ?: "Luyện tập"
                            val sourceType = backStackEntry.arguments?.getString("sourceType") ?: "DRILL"
                            val sourceId = backStackEntry.arguments?.getString("sourceId") ?: ""
                            val handModeStr = backStackEntry.arguments?.getString("handMode") ?: "RIGHT"
                            val initialHand = runCatching { HandMode.valueOf(handModeStr) }.getOrDefault(HandMode.RIGHT)
                            val initialBpm = backStackEntry.arguments?.getInt("bpm") ?: 60

                            val viewModel: PracticePlayerViewModel = viewModel(
                                factory = PracticePlayerViewModel.Factory(
                                    title = title,
                                    sourceType = sourceType,
                                    sourceId = sourceId,
                                    initialHand = initialHand,
                                    initialBpm = initialBpm,
                                    practiceEngine = appContainer.practiceEngine,
                                    midiInput = appContainer.midiInput,
                                    metronomeController = appContainer.metronomeController,
                                    curriculumRepository = appContainer.curriculumRepository,
                                    exerciseRepository = appContainer.exerciseRepository,
                                    songRepository = appContainer.songRepository,
                                    progressRepository = appContainer.progressRepository,
                                    settingsRepository = appContainer.settingsRepository
                                )
                            )
                            PracticePlayerScreen(
                                viewModel = viewModel,
                                onBackClick = { navController.popBackStack() },
                                onNavigateToResult = { sessionId ->
                                    navController.navigate(Screen.PracticeResult.createRoute(sessionId)) {
                                        popUpTo(Screen.Practice.route)
                                    }
                                }
                            )
                        }

                        // Secondary: Practice Result
                        composable(
                            route = Screen.PracticeResult.route,
                            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
                            val viewModel: PracticeResultViewModel = viewModel(
                                factory = PracticeResultViewModel.Factory(
                                    sessionId = sessionId,
                                    progressRepository = appContainer.progressRepository
                                )
                            )
                            PracticeResultScreen(
                                viewModel = viewModel,
                                onNavigateToHome = {
                                    navController.navigate(Screen.Home.route) {
                                        popUpTo(Screen.Home.route) { inclusive = true }
                                    }
                                },
                                onRetryPractice = { title, sourceType, sourceId, handMode, bpm ->
                                    navController.navigate(
                                        Screen.PracticePlayer.createRoute(
                                            title = title,
                                            sourceType = sourceType,
                                            sourceId = sourceId,
                                            handMode = handMode,
                                            bpm = bpm
                                        )
                                    ) {
                                        popUpTo(Screen.Home.route)
                                    }
                                }
                            )
                        }

                        // Secondary: Free Play
                        composable(Screen.FreePlay.route) {
                            val viewModel: FreePlayViewModel = viewModel(
                                factory = FreePlayViewModel.Factory(
                                    context = this@MainActivity,
                                    midiInput = appContainer.midiInput,
                                    metronomeController = appContainer.metronomeController,
                                    settingsRepository = appContainer.settingsRepository,
                                    freePlayRepository = appContainer.freePlayRepository
                                )
                            )
                            FreePlayScreen(
                                viewModel = viewModel,
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        // Secondary: MIDI Diagnostic
                        composable(Screen.MidiDiagnostic.route) {
                            val viewModel: MidiDiagnosticViewModel = viewModel(
                                factory = MidiDiagnosticViewModel.Factory(
                                    midiInput = appContainer.midiInput,
                                    settingsRepository = appContainer.settingsRepository
                                )
                            )
                            MidiDiagnosticScreen(
                                viewModel = viewModel,
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        // Secondary: Device Connection
                        composable(Screen.DeviceConnection.route) {
                            val viewModel: DeviceViewModel = viewModel(
                                factory = DeviceViewModel.Factory(
                                    deviceManager = appContainer.pianoDeviceManager
                                )
                            )
                            DeviceConnectionScreen(
                                viewModel = viewModel,
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        // Secondary: Settings
                        composable(Screen.Settings.route) {
                            val viewModel: SettingsViewModel = viewModel(
                                factory = SettingsViewModel.Factory(
                                    settingsRepository = appContainer.settingsRepository,
                                    progressRepository = appContainer.progressRepository
                                )
                            )
                            SettingsScreen(
                                viewModel = viewModel,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
