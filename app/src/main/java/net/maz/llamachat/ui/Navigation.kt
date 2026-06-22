package net.maz.llamachat.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import net.maz.llamachat.LlamaChatApp
import net.maz.llamachat.ui.chat.ChatScreen
import net.maz.llamachat.ui.connect.ConnectScreen
import net.maz.llamachat.ui.home.HomeScreen
import net.maz.llamachat.ui.newconv.NewConversationScreen
import net.maz.llamachat.vm.ChatViewModel
import net.maz.llamachat.vm.ConnectViewModel
import net.maz.llamachat.vm.HomeViewModel
import net.maz.llamachat.vm.NewConversationViewModel

object Routes {
    const val CONNECT = "connect"
    const val HOME = "home"
    const val NEW = "new"
    const val CHAT = "chat"
    fun chat(id: Long) = "$CHAT/$id"
    fun newConv(editId: Long? = null) = if (editId == null) NEW else "$NEW?convId=$editId"
}

@Composable
fun LlamaChatNavHost() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as LlamaChatApp

    NavHost(navController = navController, startDestination = Routes.CONNECT) {

        composable(Routes.CONNECT) {
            val vm: ConnectViewModel = viewModel(factory = ConnectViewModel.factory(app))
            ConnectScreen(
                vm = vm,
                onConnected = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.CONNECT) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(app))
            HomeScreen(
                vm = vm,
                onOpenConversation = { id -> navController.navigate(Routes.chat(id)) },
                onNewConversation = { navController.navigate(Routes.NEW) },
                onDisconnect = {
                    vm.disconnect()
                    navController.navigate(Routes.CONNECT) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
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
            )
        }
    }
}
