package net.maz.llamachat.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.maz.llamachat.LlamaChatApp
import net.maz.llamachat.data.comfy.FlowType
import net.maz.llamachat.data.gen.GenerationService
import net.maz.llamachat.data.model.Conversation
import net.maz.llamachat.ui.characters.CharacterEditScreen
import net.maz.llamachat.ui.characters.CharacterListScreen
import net.maz.llamachat.ui.characters.GeneratorScreen
import net.maz.llamachat.ui.chat.ChatScreen
import net.maz.llamachat.ui.gallery.GalleryScreen
import net.maz.llamachat.ui.gallery.ViewerScreen
import net.maz.llamachat.ui.home.HomeScreen
import net.maz.llamachat.ui.launcher.LauncherScreen
import net.maz.llamachat.ui.newconv.NewConversationScreen
import net.maz.llamachat.ui.queue.QueueScreen
import net.maz.llamachat.ui.settings.SettingsScreen
import net.maz.llamachat.ui.workflow.WorkflowFormScreen
import net.maz.llamachat.ui.workflow.WorkflowPickerScreen
import net.maz.llamachat.vm.CharacterViewModel
import net.maz.llamachat.vm.ChatViewModel
import net.maz.llamachat.vm.GalleryViewModel
import net.maz.llamachat.vm.GeneratorViewModel
import net.maz.llamachat.vm.HomeViewModel
import net.maz.llamachat.vm.NewConversationViewModel
import net.maz.llamachat.vm.QueueViewModel
import net.maz.llamachat.vm.SettingsViewModel
import net.maz.llamachat.vm.WorkflowFormViewModel
import net.maz.llamachat.vm.WorkflowPickerViewModel

/** Vision model the "Image to Text" quick chat is pinned to. */
private const val QUICK_IMAGE_MODEL = "Qwen3.6-VL-27B-NR"

object Routes {
    const val LAUNCHER = "launcher"
    const val SETTINGS = "settings"
    const val HOME = "home"
    const val NEW = "new"
    const val CHAT = "chat"
    const val CHARACTERS = "characters"
    const val CHARACTER_EDIT = "character_edit"
    const val CHARACTER_GENERATE = "character_generate"
    const val WORKFLOWS = "workflows"
    const val WORKFLOW_FORM = "workflow_form"
    const val GALLERY = "gallery"
    const val QUEUE = "queue"
    const val VIEWER = "viewer"
    fun chat(id: Long) = "$CHAT/$id"
    fun newConv(editId: Long? = null) = if (editId == null) NEW else "$NEW?convId=$editId"
    fun editCharacter(name: String? = null) =
        if (name == null) CHARACTER_EDIT else "$CHARACTER_EDIT?name=${Uri.encode(name)}"
    fun workflows(flowType: FlowType) = "$WORKFLOWS/${flowType.key}"
    fun workflowForm(workflowId: Long, fromJobId: Long = -1L) =
        "$WORKFLOW_FORM/$workflowId?fromJob=$fromJobId"
    fun gallery(flowType: FlowType? = null) =
        if (flowType == null) GALLERY else "$GALLERY?flowType=${flowType.key}"
    fun viewer(itemId: Long) = "$VIEWER/$itemId"
}

@Composable
fun LlamaChatNavHost() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as LlamaChatApp
    val openSettings: () -> Unit = { navController.navigate(Routes.SETTINGS) }

    NavHost(navController = navController, startDestination = Routes.LAUNCHER) {

        composable(Routes.LAUNCHER) {
            val scope = rememberCoroutineScope()
            LauncherScreen(
                onOpenChat = { navController.navigate(Routes.HOME) },
                onImageToText = {
                    // Reset the single scratch conversation so every tile press starts a
                    // fresh "photo + question" chat, then open it like any other chat.
                    scope.launch {
                        val id = Conversation.QUICK_IMAGE_ID
                        if (app.generation.isActive(id)) GenerationService.cancel(app)
                        withContext(Dispatchers.IO) { app.attachmentStore.deleteAll(id) }
                        val now = System.currentTimeMillis()
                        app.conversationRepository.save(
                            Conversation(
                                id = id,
                                title = "Image to Text",
                                characterName = "Assistant",
                                presetName = "Default",
                                model = QUICK_IMAGE_MODEL,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        )
                        navController.navigate(Routes.chat(id))
                    }
                },
                onOpenFlow = { flowType -> navController.navigate(Routes.workflows(flowType)) },
                onOpenGallery = { navController.navigate(Routes.gallery()) },
                onOpenQueue = { navController.navigate(Routes.QUEUE) },
                onOpenSettings = openSettings,
            )
        }

        composable(
            route = "${Routes.WORKFLOWS}/{flowType}",
            arguments = listOf(navArgument("flowType") { type = NavType.StringType }),
        ) { backStackEntry ->
            val flowType = FlowType.fromKey(backStackEntry.arguments?.getString("flowType").orEmpty())
                ?: return@composable
            val vm: WorkflowPickerViewModel =
                viewModel(factory = WorkflowPickerViewModel.factory(app, flowType))
            WorkflowPickerScreen(
                vm = vm,
                flowType = flowType,
                onSelect = { id -> navController.navigate(Routes.workflowForm(id)) },
                onOpenGallery = { navController.navigate(Routes.gallery(flowType)) },
                onBack = { navController.popBackStack() },
                onOpenSettings = openSettings,
            )
        }

        composable(
            route = "${Routes.WORKFLOW_FORM}/{workflowId}?fromJob={jobId}",
            arguments = listOf(
                navArgument("workflowId") { type = NavType.LongType },
                navArgument("jobId") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) { backStackEntry ->
            val workflowId = backStackEntry.arguments?.getLong("workflowId") ?: return@composable
            val fromJobId = backStackEntry.arguments?.getLong("jobId") ?: -1L
            val vm: WorkflowFormViewModel =
                viewModel(factory = WorkflowFormViewModel.factory(app, workflowId, fromJobId))
            WorkflowFormScreen(
                vm = vm,
                // Land on the queue so the new job's progress is immediately visible;
                // back returns to the picker.
                onSubmitted = {
                    navController.navigate(Routes.QUEUE) {
                        popUpTo("${Routes.WORKFLOW_FORM}/{workflowId}?fromJob={jobId}") { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
                onOpenSettings = openSettings,
            )
        }

        composable(
            route = "${Routes.GALLERY}?flowType={flowType}",
            arguments = listOf(navArgument("flowType") { type = NavType.StringType; defaultValue = "" }),
        ) { backStackEntry ->
            val initialTab = FlowType.fromKey(backStackEntry.arguments?.getString("flowType").orEmpty())
            val vm: GalleryViewModel =
                viewModel(factory = GalleryViewModel.factory(app, initialTab))
            GalleryScreen(
                vm = vm,
                onOpenItem = { id -> navController.navigate(Routes.viewer(id)) },
                onBack = { navController.popBackStack() },
                onOpenSettings = openSettings,
            )
        }

        composable(Routes.QUEUE) {
            val vm: QueueViewModel = viewModel(factory = QueueViewModel.factory(app))
            QueueScreen(
                vm = vm,
                onRegenerate = { job ->
                    navController.navigate(Routes.workflowForm(job.workflowId, job.id))
                },
                onOpenOutput = { itemId -> navController.navigate(Routes.viewer(itemId)) },
                onBack = { navController.popBackStack() },
                onOpenSettings = openSettings,
            )
        }

        composable(
            route = "${Routes.VIEWER}/{itemId}",
            arguments = listOf(navArgument("itemId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getLong("itemId") ?: return@composable
            val vm: GalleryViewModel = viewModel(factory = GalleryViewModel.factory(app))
            ViewerScreen(
                vm = vm,
                itemId = itemId,
                onRegenerate = { job ->
                    navController.navigate(Routes.workflowForm(job.workflowId, job.id))
                },
                onBack = { navController.popBackStack() },
                onOpenSettings = openSettings,
            )
        }

        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(app))
            SettingsScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.HOME) {
            val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(app))
            HomeScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onOpenConversation = { id -> navController.navigate(Routes.chat(id)) },
                onEditConversation = { id -> navController.navigate(Routes.newConv(id)) },
                onNewConversation = { navController.navigate(Routes.NEW) },
                onManageCharacters = { navController.navigate(Routes.CHARACTERS) },
                onOpenSettings = openSettings,
            )
        }

        composable(
            route = "${Routes.NEW}?convId={convId}",
            arguments = listOf(navArgument("convId") { type = NavType.LongType; defaultValue = -1L }),
        ) { backStackEntry ->
            val editId = backStackEntry.arguments?.getLong("convId")?.takeIf { it >= 0 }
            val vm: NewConversationViewModel =
                viewModel(factory = NewConversationViewModel.factory(app, editId))
            NewConversationScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onStarted = { id ->
                    navController.navigate(Routes.chat(id)) {
                        popUpTo("${Routes.NEW}?convId={convId}") { inclusive = true }
                    }
                },
                onOpenSettings = openSettings,
            )
        }

        composable(Routes.CHARACTERS) {
            val vm: CharacterViewModel = viewModel(factory = CharacterViewModel.factory(app))
            CharacterListScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onEdit = { name -> navController.navigate(Routes.editCharacter(name)) },
                onCreate = { navController.navigate(Routes.editCharacter()) },
                onGenerate = { navController.navigate(Routes.CHARACTER_GENERATE) },
                onOpenSettings = openSettings,
            )
        }

        composable(Routes.CHARACTER_GENERATE) {
            val vm: GeneratorViewModel = viewModel(factory = GeneratorViewModel.factory(app))
            GeneratorScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                // The saved character lands in the list we came from.
                onSaved = { navController.popBackStack() },
                onOpenSettings = openSettings,
            )
        }

        composable(
            route = "${Routes.CHARACTER_EDIT}?name={name}",
            arguments = listOf(navArgument("name") { type = NavType.StringType; defaultValue = "" }),
        ) { backStackEntry ->
            val name = backStackEntry.arguments?.getString("name")?.takeIf { it.isNotEmpty() }
            val vm: CharacterViewModel = viewModel(factory = CharacterViewModel.factory(app))
            CharacterEditScreen(
                vm = vm,
                editName = name,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onOpenSettings = openSettings,
            )
        }

        composable(
            route = "${Routes.CHAT}/{convId}",
            arguments = listOf(navArgument("convId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val convId = backStackEntry.arguments?.getLong("convId") ?: return@composable
            val vm: ChatViewModel = viewModel(factory = ChatViewModel.factory(app, convId))
            ChatScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onEditDetails = { id ->
                    navController.navigate(Routes.newConv(id))
                },
                onOpenSettings = openSettings,
            )
        }
    }
}
