package io.github.radixtrator.resync.ui.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.UserWhere
import io.github.radixtrator.resync.ui.common.AppIcons
import io.github.radixtrator.resync.ui.navigation.InboxRoute
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.LoginRoute
import io.github.radixtrator.resync.ui.navigation.SearchRoute
import io.github.radixtrator.resync.ui.navigation.SettingsRoute
import io.github.radixtrator.resync.ui.navigation.SubmitRoute
import io.github.radixtrator.resync.ui.navigation.UserRoute

@Composable
fun AppDrawer(
    container: AppContainer,
    current: String?,
    subscriptions: List<String>,
    close: () -> Unit,
    onPick: (String?) -> Unit,
) {
    val nav = LocalNavigator.current
    val username by container.username.collectAsStateWithLifecycle()
    val accounts by container.accounts.collectAsStateWithLifecycle()
    val inboxCount by container.inboxCount.collectAsStateWithLifecycle()
    var goToOpen by rememberSaveable { mutableStateOf(false) }

    fun go(action: () -> Unit) {
        close()
        action()
    }

    if (goToOpen) {
        GoToSubredditDialog(
            onGo = { goToOpen = false; go { onPick(it) } },
            onDismiss = { goToOpen = false },
        )
    }

    ModalDrawerSheet {
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                AccountHeader(
                    username = username,
                    accounts = accounts,
                    onSwitch = { name -> go { container.switchAccount(name) } },
                    onAdd = { go { nav.push(LoginRoute) } },
                    onLogout = { go { container.logout() } },
                )
            }
            item { DrawerEntry("Home", Icons.Default.Home, current == null) { go { onPick(null) } } }
            item { DrawerEntry("Popular", Icons.Default.Star, current == "popular") { go { onPick("popular") } } }
            item { DrawerEntry("All", AppIcons.Sort, current == "all") { go { onPick("all") } } }
            username?.let { me ->
                item {
                    DrawerEntry("Inbox", Icons.Default.Email, false, badge = inboxCount.takeIf { it > 0 }) { go { nav.push(InboxRoute) } }
                }
                item { DrawerEntry("Profile", Icons.Default.Person, false) { go { nav.push(UserRoute(me)) } } }
                item { DrawerEntry("Saved", AppIcons.Bookmark, false) { go { nav.push(UserRoute(me, UserWhere.SAVED)) } } }
                item { DrawerEntry("Create post", Icons.Default.Create, false) { go { nav.push(SubmitRoute()) } } }
            }
            item { DrawerEntry("Search", Icons.Default.Search, false) { go { nav.push(SearchRoute()) } } }
            item { DrawerEntry("Go to subreddit…", AppIcons.OpenExternal, false) { goToOpen = true } }
            item { DrawerEntry("Settings", Icons.Default.Settings, false) { go { nav.push(SettingsRoute) } } }
            if (subscriptions.isNotEmpty()) {
                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 24.dp))
                    Text(
                        "Subscriptions",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                    )
                }
                items(subscriptions, key = { it }) { name ->
                    DrawerEntry("r/$name", null, current.equals(name, ignoreCase = true)) { go { onPick(name) } }
                }
            }
        }
    }
}

@Composable
private fun AccountHeader(
    username: String?,
    accounts: List<String>,
    onSwitch: (String?) -> Unit,
    onAdd: () -> Unit,
    onLogout: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
        Text("Resync", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (username == null && accounts.isEmpty()) {
            Text("Browsing logged out", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAdd) { Text("Log in") }
            return@Column
        }
        androidx.compose.foundation.layout.Box {
            Row(
                Modifier.clickable { menuOpen = true }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.AccountCircle, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.padding(4.dp))
                Text(username?.let { "u/$it" } ?: "Logged out", style = MaterialTheme.typography.titleMedium)
                Icon(Icons.Default.ArrowDropDown, "Switch account")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                accounts.forEach { name ->
                    DropdownMenuItem(
                        text = { Text("u/$name", fontWeight = if (name == username) FontWeight.Bold else null) },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        onClick = { menuOpen = false; onSwitch(name) },
                    )
                }
                DropdownMenuItem(text = { Text("Browse logged out") }, onClick = { menuOpen = false; onSwitch(null) })
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Add account") },
                    leadingIcon = { Icon(Icons.Default.Add, null) },
                    onClick = { menuOpen = false; onAdd() },
                )
                if (username != null) {
                    DropdownMenuItem(
                        text = { Text("Log out u/$username") },
                        leadingIcon = { Icon(Icons.Default.ExitToApp, null) },
                        onClick = { menuOpen = false; onLogout() },
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerEntry(label: String, icon: ImageVector?, selected: Boolean, badge: Int? = null, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        icon = icon?.let { { Icon(it, null) } },
        badge = badge?.let { { Badge { Text("$it") } } },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

// A dialog rather than an inline field: the drawer focuses any text field in it on open,
// which pops the keyboard every time.
@Composable
private fun GoToSubredditDialog(onGo: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val submit = { if (name.isNotBlank()) onGo(name) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to subreddit") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.trim().removePrefix("/").removePrefix("r/") },
                prefix = { Text("r/") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = submit, enabled = name.isNotBlank()) { Text("Go") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
