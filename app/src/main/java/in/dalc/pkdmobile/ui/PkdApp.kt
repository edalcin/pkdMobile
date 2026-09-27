package `in`.dalc.pkdmobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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

/**
 * Casca de navegação da v1 (direção A · Notas-first): login, depois barra inferior com 4 abas.
 * Notas já usam o cache Room; as outras abas ainda mostram um estado vazio (ver docs/proximosPassos.md).
 */

private enum class PkdTab(val route: String, val title: String, val icon: ImageVector, val hasFab: Boolean) {
    Notas("notas", "Notas", Icons.Filled.Description, hasFab = true),
    Memorias("memorias", "Memórias", Icons.Filled.History, hasFab = true),
    Documentos("documentos", "Documentos", Icons.Filled.Folder, hasFab = false),
    Busca("busca", "Busca", Icons.Filled.Search, hasFab = false),
}

private fun emptyStateFor(tab: PkdTab): String = when (tab) {
    PkdTab.Notas -> ""
    PkdTab.Memorias -> "Nenhuma Memória ainda"
    PkdTab.Documentos -> "Nenhum Documento ainda"
    PkdTab.Busca -> "Digite para buscar"
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
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
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
                NotesScreen(onOpen = { navController.navigate("nota/$it") }, onUnsent = { navController.navigate("naoenviados") })
            }
            composable("naoenviados") { UnsentScreen(onBack = { navController.popBackStack() }) }
            PkdTab.entries.filter { it != PkdTab.Notas }.forEach { tab ->
                composable(tab.route) { PkdTabScreen(tab) }
            }
            composable("nota/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                NoteDetailScreen(it.arguments!!.getLong("id"), onBack = { navController.popBackStack() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PkdTabScreen(tab: PkdTab) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(tab.title) }) },
        floatingActionButton = {
            if (tab.hasFab) {
                // ponytail: FAB ainda não cria Nota/Memória — vem no slice de cache + fila de envio.
                FloatingActionButton(onClick = { }) {
                    Icon(Icons.Filled.Add, contentDescription = "Novo")
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = emptyStateFor(tab), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
