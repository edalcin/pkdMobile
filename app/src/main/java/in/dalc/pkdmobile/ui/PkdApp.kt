package `in`.dalc.pkdmobile.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.compose.runtime.LaunchedEffect
import `in`.dalc.pkdmobile.data.Notes
import `in`.dalc.pkdmobile.data.Session
import androidx.compose.foundation.layout.WindowInsets

/** Casca de navegação da v1 (direção A · Notas-first): login, depois barra inferior com 4 abas. */

private enum class PkdTab(val route: String, val title: String, val icon: String) {
    Notas("notas", "Notas", "bx-note"),
    Memorias("memorias", "Memórias", "bx-history"),
    Documentos("documentos", "Documentos", "bx-folder"),
    Busca("busca", "Busca", "bx-search"),
}

@Composable
fun PkdApp() {
    if (!Session.loggedIn) {
        LoginScreen()
        return
    }
    LaunchedEffect(Unit) { Notes.refresh() } // right after login

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        contentWindowInsets = WindowInsets(0), // each screen's own Scaffold handles the system bars
        bottomBar = {
            if (PkdTab.entries.none { it.route == currentRoute }) return@Scaffold // Detalhe: tela cheia
            NavigationBar {
                PkdTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Boxicons.Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.title) },
                    )
                }
            }
        },
    ) { scaffoldPadding ->
        NavHost(
            navController = navController,
            startDestination = PkdTab.Notas.route,
            modifier = Modifier.padding(scaffoldPadding),
        ) {
            composable(PkdTab.Notas.route) {
                NotesScreen(
                    onOpen = { navController.navigate("nota/$it") },
                    onUnsent = { navController.navigate("naoenviados") },
                    onNewNote = { navController.navigate("novanota") },
                )
            }
            composable("novanota") {
                NewNoteScreen(
                    initialTitle = "", initialHtml = "<p></p>", autoFocusBody = true,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable("naoenviados") {
                UnsentScreen(
                    onBack = { navController.popBackStack() },
                    onRecriar = { seq -> navController.navigate("recriar/$seq") },
                )
            }
            composable("recriar/{seq}", arguments = listOf(navArgument("seq") { type = NavType.LongType })) {
                RecriarScreen(it.arguments!!.getLong("seq"), onBack = { navController.popBackStack() })
            }
            composable(PkdTab.Memorias.route) { MemoriesScreen(onOpen = { navController.navigate("memoria/$it") }) }
            composable(PkdTab.Documentos.route) { DocumentsScreen(onOpen = { navController.navigate("documento/$it") }) }
            composable(PkdTab.Busca.route) {
                SearchScreen(onOpen = { hit ->
                    navController.navigate(
                        when (hit.kind) {
                            Notes.Kind.Nota -> "nota/${hit.id}"
                            Notes.Kind.Memoria -> "memoria/${hit.id}"
                            Notes.Kind.Documento -> "documento/${hit.id}"
                        },
                    )
                })
            }
            composable("documento/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                DocumentDetailScreen(
                    it.arguments!!.getLong("id"),
                    onBack = { navController.popBackStack() },
                    onOpenDoc = { id -> navController.navigate("documento/$id") },
                    onOpenNote = { id -> navController.navigate("nota/$id") },
                )
            }
            composable("nota/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                NoteDetailScreen(
                    it.arguments!!.getLong("id"),
                    onBack = { navController.popBackStack() },
                    onOpenDoc = { id -> navController.navigate("documento/$id") },
                    onOpenNote = { id -> navController.navigate("nota/$id") },
                )
            }
            composable("memoria/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                MemoryDetailScreen(it.arguments!!.getLong("id"), onBack = { navController.popBackStack() })
            }
        }
    }
}
