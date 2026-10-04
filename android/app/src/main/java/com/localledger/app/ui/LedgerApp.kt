package com.localledger.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.localledger.app.R
import com.localledger.app.data.repository.AssetRepository
import com.localledger.app.ui.assets.*
import com.localledger.app.ui.common.LocalHideAmounts
import com.localledger.app.data.repository.MemoRepository
import com.localledger.app.data.repository.WishRepository
import com.localledger.app.data.repository.PlanningRepository
import com.localledger.app.data.repository.SettingsRepository
import com.localledger.app.data.repository.ImportRepository
import com.localledger.app.ui.memo.*
import com.localledger.app.ui.wishes.*
import com.localledger.app.ui.planning.*
import com.localledger.app.ui.settings.*
import com.localledger.app.ui.importing.*
import com.localledger.app.data.repository.LedgerRepository
import com.localledger.app.ui.add.EntryScreen
import com.localledger.app.ui.add.EntryViewModel
import com.localledger.app.ui.category.CategoryScreen
import com.localledger.app.ui.category.CategoryViewModel
import com.localledger.app.ui.home.HomeScreen
import com.localledger.app.ui.home.OverviewCards
import com.localledger.app.ui.home.HomeViewModel
import com.localledger.app.ui.stats.StatsScreen
import com.localledger.app.ui.stats.StatsViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.localledger.app.data.repository.LifeRepository
import com.localledger.app.ui.life.LifeViewModel
import com.localledger.app.ui.life.LifeScreen
import com.localledger.app.ui.accounts.AccountsViewModel
import com.localledger.app.ui.accounts.AccountsScreen
import com.localledger.app.data.repository.RecycleRepository
import com.localledger.app.ui.settings.RecycleScreen
import com.localledger.app.data.repository.CaptureRepository
import com.localledger.app.ui.importing.CaptureScreen
import com.localledger.app.domain.paymentHint
import com.localledger.app.domain.formatAmount
import com.localledger.app.domain.EXPENSE

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerApp(
    repository: LedgerRepository,
    assetRepository: AssetRepository,
    memoRepository: MemoRepository,
    wishRepository: WishRepository,
    planningRepository: PlanningRepository,
    lifeRepository: LifeRepository,
    recycleRepository: RecycleRepository,
    captureRepository: CaptureRepository,
    settingsRepository: SettingsRepository,
    importRepository: ImportRepository,
    versionName: String,
    onNotifications: () -> Unit,
    onCaptureAccess: () -> Unit,
    onShortcut: (String) -> Unit,
    requestedRoute: String? = null,
    onRouteHandled: () -> Unit = {},
    onUpdate: () -> Unit = {},
    onExport: () -> Unit = {},
    onImport: () -> Unit = {},
) {
    val settings by settingsRepository.settings.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalHideAmounts provides settings.hideAmounts) {
        val memoNavigation = rememberNavController()
        val ledgerNavigation = rememberNavController()
        val modeStates = rememberSaveableStateHolder()
        var memoMode by rememberSaveable { mutableStateOf(settings.defaultHome == "memos") }
        val navigation = if (memoMode) memoNavigation else ledgerNavigation
        val startRoute = if (memoMode) "memos" else "home"
        fun openMain(destination: String) {
            navigation.navigate(destination) {
                popUpTo(navigation.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        LaunchedEffect(requestedRoute, memoMode) { requestedRoute?.let {
            val nextMemoMode = (it.startsWith("memo/") || it in listOf("memos", "tasks", "life", "wishes"))
            if (nextMemoMode != memoMode) memoMode = nextMemoMode
            else { navigation.navigate(it) { launchSingleTop = true }; onRouteHandled() }
        } }
        val backStack by navigation.currentBackStackEntryAsState()
        val route = backStack?.destination?.route ?: startRoute
        val memoKind = if (route == "memos" && backStack != null) {
            val model: MemosViewModel = viewModel(viewModelStoreOwner = backStack!!, factory = remember(memoRepository) {
                viewModelFactory { initializer { MemosViewModel(memoRepository, createSavedStateHandle()) } }
            })
            val kind by model.kind.collectAsStateWithLifecycle()
            kind
        } else "note"
        val addingTask = route == "memos" && memoKind == "todo"
        val editing = route == "capture-entry" || route == "add" || route == "edit/{id}" || route == "copy/{id}" || route.startsWith("asset/") || route.startsWith("memo/") || route.startsWith("wish/")
        val mainRoute = route in listOf("home", "memos", "wishes", "assets", "stats", "settings", "life")
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        var menuOpen by remember { mutableStateOf(false) }
        var captureText by rememberSaveable { mutableStateOf("") }
        var entrySaving by remember { mutableStateOf(false) }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (mainRoute) SingleChoiceSegmentedButtonRow(Modifier.width(208.dp)) {
                            listOf(true to "备忘录", false to "记账").forEachIndexed { index, (mode, label) ->
                                SegmentedButton(selected = memoMode == mode, onClick = { memoMode = mode },
                                    shape = SegmentedButtonDefaults.itemShape(index, 2), icon = {},
                                    border = SegmentedButtonDefaults.borderStroke(color = Color.Transparent),
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        activeContentColor = MaterialTheme.colorScheme.primary,
                                        inactiveContainerColor = MaterialTheme.colorScheme.surface,
                                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant)) { Text(label, maxLines = 1) }
                            }
                        } else Text(when (route) {
                        "assets" -> "物品"
                        "asset/add" -> "添加物品"
                        "asset/edit/{id}" -> "编辑物品"
                        "stats" -> "报表分析"
                        "settings" -> "设置"
                        "life" -> "生活"
                        "tasks" -> "待办事项"
                        "accounts" -> "账户管理"
                        "reimbursement" -> "报销管理"
                        "recycle" -> "回收站"
                        "capture" -> "截图与自动采集"
                        "capture-entry" -> "核对账目"
                        "planning" -> "预算与固定账目"
                        "import" -> "导入账单"
                        "memos" -> "备忘录"
                        "memo/task" -> "新建待办"
                        "memo/add" -> "新建备忘录"
                        "memo/edit/{id}" -> "编辑备忘录"
                        "wish/add" -> "添加心愿"
                        "wish/edit/{id}" -> "编辑心愿"
                        "category" -> "分类管理"
                        "add" -> "记一笔"
                        "edit/{id}" -> "编辑账目"
                        "copy/{id}" -> "再记一笔"
                        else -> "随手账"
                    }) },
                    navigationIcon = {
                        if (!mainRoute) IconButton(enabled = !entrySaving, onClick = { navigation.popBackStack() }) {
                            Icon(painterResource(R.drawable.ic_ui_back), contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (!editing) {
                            if (route == "wishes") IconButton(onClick = { navigation.navigate("wish/add") }) {
                                Icon(painterResource(R.drawable.ic_ui_plus), "添加心愿")
                            }
                            IconButton(onClick = { menuOpen = true }) { Icon(painterResource(R.drawable.ic_ui_more), "更多功能") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text("设置") }, onClick = { menuOpen = false; openMain("settings") })
                                DropdownMenuItem(text = { Text(if (memoMode) "切换到记账" else "切换到备忘录") }, onClick = { menuOpen = false; memoMode = !memoMode })
                                if (memoMode) DropdownMenuItem(text = { Text("待办事项") }, onClick = { menuOpen = false; navigation.navigate("tasks") })
                                if (!memoMode) DropdownMenuItem(text = { Text("截图与自动采集") }, onClick = { menuOpen = false; navigation.navigate("capture") })
                                if (!memoMode) DropdownMenuItem(text = { Text("账户管理") }, onClick = { menuOpen = false; navigation.navigate("accounts") })
                                if (!memoMode) DropdownMenuItem(text = { Text("报销管理") }, onClick = { menuOpen = false; navigation.navigate("reimbursement") })
                                if (!memoMode) DropdownMenuItem(text = { Text("预算与固定账目") }, onClick = { menuOpen = false; navigation.navigate("planning") })
                                DropdownMenuItem(text = { Text("应用更新") }, onClick = { menuOpen = false; onUpdate() })
                                DropdownMenuItem(text = { Text("导出完整备份") }, onClick = { menuOpen = false; onExport() })
                                DropdownMenuItem(text = { Text("恢复完整备份") }, onClick = { menuOpen = false; onImport() })
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
            bottomBar = {
                if (mainRoute) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    val pages = if (memoMode) listOf("memos" to "备忘录", "life" to "生活", "wishes" to "心愿", "settings" to "设置")
                        else listOf("home" to "账单", "assets" to "物品", "stats" to "报表", "settings" to "设置")
                    pages.forEach { (destination, label) ->
                        NavigationBarItem(
                            selected = route == destination,
                            onClick = { openMain(destination) },
                            icon = { Icon(painterResource(when (destination) {
                                "home" -> R.drawable.ic_ui_wallet
                                "life" -> R.drawable.ic_ui_calendar
                                "memos" -> R.drawable.ic_ui_notebook
                                "wishes" -> R.drawable.ic_ui_heart
                                "assets" -> R.drawable.ic_ui_package
                                "stats" -> R.drawable.ic_ui_report
                                else -> R.drawable.ic_ui_settings
                            }), contentDescription = null) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                        )
                    }
                }
            },
            floatingActionButton = {
                if (route in listOf("home", "assets", "memos")) ExtendedFloatingActionButton(
                    modifier = Modifier.semantics { contentDescription = when(route) { "assets" -> "添加物品"; "memos" -> if (addingTask) "新建待办" else "新建备忘录"; else -> "记一笔" } },
                    onClick = { navigation.navigate(when(route) { "assets" -> "asset/add"; "memos" -> if (addingTask) "memo/task" else "memo/add"; else -> "add" }) },
                    icon = { Icon(painterResource(R.drawable.ic_ui_plus), null) },
                    text = { Text(when(route) { "assets" -> "添加物品"; "memos" -> if (addingTask) "新建待办" else "新建备忘录"; else -> "记一笔" }) },
                    containerColor = if (route == "memos") Color(0xFFF3DF9A) else Color(0xFFF2A35A),
                    contentColor = Color(0xFF382A1C),
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            modeStates.SaveableStateProvider(memoMode) {
                NavHost(navigation, startDestination = startRoute, route = if (memoMode) "memo-mode" else "ledger-mode", modifier = Modifier.padding(padding)) {
                    if (!memoMode) {
                        composable("home") {
                            val model: HomeViewModel = viewModel(factory = remember(repository) {
                                viewModelFactory { initializer { HomeViewModel(repository) } }
                            })
                            HomeScreen(model, snackbar, onEdit = { navigation.navigate("edit/$it") }, onCopy = { navigation.navigate("copy/$it") },
                                onAccounts = { navigation.navigate("accounts") }, onAddTransaction = { navigation.navigate("add") }, onPlanning = { navigation.navigate("planning") },
                                overview = { OverviewCards(repository, assetRepository, { navigation.navigate("accounts") }, { openMain("assets") }) })
                        }
                        composable("accounts") {
                            val model: AccountsViewModel = viewModel(factory = remember(repository) {
                                viewModelFactory { initializer { AccountsViewModel(repository) } }
                            })
                            AccountsScreen(model)
                        }
                        composable("reimbursement") {
                            val model: HomeViewModel = viewModel(factory = remember(repository) {
                                viewModelFactory { initializer { HomeViewModel(repository).apply { showReimbursements() } } }
                            })
                            HomeScreen(model, snackbar, onEdit = { navigation.navigate("edit/$it") })
                        }
                        composable("assets") {
                            val model: AssetsViewModel = viewModel(factory = remember(assetRepository) {
                                viewModelFactory { initializer { AssetsViewModel(assetRepository) } }
                            })
                            AssetsScreen(model) { navigation.navigate("asset/edit/$it") }
                        }
                        composable("asset/add") {
                            val model: AssetEditorViewModel = viewModel(factory = remember(assetRepository) {
                                viewModelFactory { initializer { AssetEditorViewModel(assetRepository, createSavedStateHandle(), null) } }
                            })
                            AssetEditorScreen(model, { entrySaving = it }) { navigation.popBackStack() }
                        }
                        composable("asset/edit/{id}") { entry ->
                            val id = requireNotNull(entry.arguments?.getString("id"))
                            val model: AssetEditorViewModel = viewModel(factory = remember(assetRepository, id) {
                                viewModelFactory { initializer { AssetEditorViewModel(assetRepository, createSavedStateHandle(), id) } }
                            })
                            AssetEditorScreen(model, { entrySaving = it }) { navigation.popBackStack() }
                        }
                        composable("stats") {
                            val model: StatsViewModel = viewModel(factory = remember(repository) {
                                viewModelFactory { initializer { StatsViewModel(repository, createSavedStateHandle()) } }
                            })
                            StatsScreen(model, onEdit = { navigation.navigate("edit/$it") })
                        }
                        composable("category") {
                            val model: CategoryViewModel = viewModel(factory = remember(repository) {
                                viewModelFactory { initializer { CategoryViewModel(repository) } }
                            })
                            CategoryScreen(model, snackbar)
                        }
                    }
                    composable("settings") {
                        val model: SettingsViewModel = viewModel(factory = remember(settingsRepository) {
                            viewModelFactory { initializer { SettingsViewModel(settingsRepository, versionName) } }
                        })
                        SettingsScreen(model, memoMode = memoMode, onCategories = { navigation.navigate("category") }, onPlans = { navigation.navigate("planning") },
                            onExport = onExport, onRestore = onImport, onImportCsv = { navigation.navigate("import") },
                            onUpdate = onUpdate, onNotifications = onNotifications, onShortcut = onShortcut,
                            onRecycle = { navigation.navigate("recycle") },
                            onCapture = { navigation.navigate("capture") }, onCaptureAccess = onCaptureAccess)
                    }
                    composable("recycle") { RecycleScreen(recycleRepository, memoMode) }
                    if (!memoMode) {
                        composable("planning") {
                            val model: PlanningViewModel = viewModel(factory = remember(planningRepository, repository) {
                                viewModelFactory { initializer { PlanningViewModel(planningRepository, repository, createSavedStateHandle()) } }
                            })
                            PlanningScreen(model)
                        }
                        composable("capture") {
                            CaptureScreen(captureRepository) { input ->
                                scope.launch {
                                    if (input.startsWith("candidate:")) {
                                        val candidate = captureRepository.candidate(input.removePrefix("candidate:"))
                                        if (candidate == null) snackbar.showSnackbar("这条通知已经处理")
                                        else { captureText = input; navigation.navigate("capture-entry") }
                                    } else { captureText = input; navigation.navigate("capture-entry") }
                                }
                            }
                        }
                        composable("capture-entry") {
                            var ready by remember { mutableStateOf(!captureText.startsWith("candidate:")) }
                            var raw by rememberSaveable { mutableStateOf(captureText.takeUnless { it.startsWith("candidate:") }.orEmpty()) }
                            var source by rememberSaveable { mutableStateOf("") }
                            var captureId by rememberSaveable { mutableStateOf<String?>(null) }
                            var capturedAt by rememberSaveable { mutableStateOf<Long?>(null) }
                            var accountId by rememberSaveable { mutableStateOf<String?>(null) }
                            LaunchedEffect(captureText) {
                                if (captureText.startsWith("candidate:")) {
                                    val row = captureRepository.candidate(captureText.removePrefix("candidate:"))
                                    if (row == null) { navigation.popBackStack(); snackbar.showSnackbar("这条通知已处理") }
                                    else {
                                        raw = row.text; source = row.source; captureId = row.id; capturedAt = row.capturedAt
                                        accountId = repository.accounts.first().firstOrNull { !it.isDeleted && it.name == source }?.id
                                        ready = true
                                    }
                                }
                            }
                            if (ready) {
                                val model: EntryViewModel = viewModel(factory = remember(repository, raw) {
                                    viewModelFactory { initializer {
                                        val handle = createSavedStateHandle()
                                        if (handle.get<Boolean>("captureInitialized") != true) {
                                            val hint = paymentHint(raw)
                                            handle["amount"] = hint.amountMinor?.let(::formatAmount).orEmpty()
                                            handle["type"] = hint.type ?: EXPENSE
                                            handle["merchant"] = hint.merchant.orEmpty()
                                            handle["note"] = raw.take(4_000)
                                            handle["captureSource"] = 2
                                            handle["captureId"] = captureId
                                            handle["captureKey"] = captureId?.let { "notification:$it" }
                                            accountId?.let { handle["accountId"] = it }
                                            capturedAt?.let {
                                                handle["originalOccurredAt"] = it
                                                handle["date"] = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
                                            }
                                            handle["captureInitialized"] = true
                                        }
                                        EntryViewModel(repository, handle, null)
                                    } }
                                })
                                EntryScreen(model, onSavingChanged = { entrySaving = it }) { navigation.popBackStack(); scope.launch { snackbar.showSnackbar("账目已保存") } }
                            }
                        }
                        composable("import") {
                            val model: ImportViewModel = viewModel(factory = remember(importRepository, repository) {
                                viewModelFactory { initializer { ImportViewModel(importRepository, repository) } }
                            })
                            ImportScreen(model)
                        }
                    }
                    if (memoMode) {
                        composable("life") {
                            val model: LifeViewModel = viewModel(factory = remember(lifeRepository) {
                                viewModelFactory { initializer { LifeViewModel(lifeRepository, createSavedStateHandle()) } }
                            })
                            LifeScreen(model, onWishes = { openMain("wishes") }, onTasks = { navigation.navigate("tasks") })
                        }
                        composable("tasks") {
                            val model: MemosViewModel = viewModel(factory = remember(memoRepository) {
                                viewModelFactory { initializer { MemosViewModel(memoRepository, createSavedStateHandle().apply { set("kind", "todo") }) } }
                            })
                            MemosScreen(model, onAdd = { kind -> navigation.navigate(if (kind == "todo") "memo/task" else "memo/add") }, onEdit = { navigation.navigate("memo/edit/$it") })
                        }
                        composable("memo/task") {
                            val model: MemoEditorViewModel = viewModel(factory = remember(memoRepository) {
                                viewModelFactory { initializer { MemoEditorViewModel(memoRepository, createSavedStateHandle().apply { set("kind", "todo") }, null) } }
                            })
                            MemoEditorScreen(model, { entrySaving = it }) { navigation.popBackStack() }
                        }
                        composable("memos") {
                            val model: MemosViewModel = viewModel(factory = remember(memoRepository) {
                                viewModelFactory { initializer { MemosViewModel(memoRepository, createSavedStateHandle()) } }
                            })
                            MemosScreen(model, onAdd = { kind -> navigation.navigate(if (kind == "todo") "memo/task" else "memo/add") }, onEdit = { navigation.navigate("memo/edit/$it") })
                        }
                        composable("memo/add") {
                            val model: MemoEditorViewModel = viewModel(factory = remember(memoRepository) {
                                viewModelFactory { initializer { MemoEditorViewModel(memoRepository, createSavedStateHandle(), null) } }
                            })
                            MemoEditorScreen(model, { entrySaving = it }) { navigation.popBackStack() }
                        }
                        composable("wishes") {
                            val model: WishesViewModel = viewModel(factory = remember(wishRepository) {
                                viewModelFactory { initializer { WishesViewModel(wishRepository, createSavedStateHandle()) } }
                            })
                            WishesScreen(model, onAdd = { navigation.navigate("wish/add") }, onEdit = { navigation.navigate("wish/edit/$it") })
                        }
                        composable("wish/add") {
                            val model: WishEditorViewModel = viewModel(factory = remember(wishRepository) {
                                viewModelFactory { initializer { WishEditorViewModel(wishRepository, createSavedStateHandle(), null) } }
                            })
                            WishEditorScreen(model, { entrySaving = it }) { navigation.popBackStack() }
                        }
                        composable("wish/edit/{id}") { entry ->
                            val id = requireNotNull(entry.arguments?.getString("id"))
                            val model: WishEditorViewModel = viewModel(factory = remember(wishRepository, id) {
                                viewModelFactory { initializer { WishEditorViewModel(wishRepository, createSavedStateHandle(), id) } }
                            })
                            WishEditorScreen(model, { entrySaving = it }) { navigation.popBackStack() }
                        }
                        composable("memo/edit/{id}") { entry ->
                            val id = requireNotNull(entry.arguments?.getString("id"))
                            val model: MemoEditorViewModel = viewModel(factory = remember(memoRepository, id) {
                                viewModelFactory { initializer { MemoEditorViewModel(memoRepository, createSavedStateHandle(), id) } }
                            })
                            MemoEditorScreen(model, { entrySaving = it }) { navigation.popBackStack() }
                        }
                    }
                    if (!memoMode) {
                        composable("add") {
                            val model: EntryViewModel = viewModel(factory = remember(repository) {
                                viewModelFactory { initializer { EntryViewModel(repository, createSavedStateHandle(), null) } }
                            })
                            EntryScreen(model, onSavingChanged = { entrySaving = it }) {
                                navigation.popBackStack()
                                scope.launch { snackbar.showSnackbar("账目已保存") }
                            }
                        }
                        composable("edit/{id}") { entry ->
                            val id = requireNotNull(entry.arguments?.getString("id"))
                            val model: EntryViewModel = viewModel(factory = remember(repository, id) {
                                viewModelFactory { initializer { EntryViewModel(repository, createSavedStateHandle(), id) } }
                            })
                            EntryScreen(model, onSavingChanged = { entrySaving = it }) {
                                navigation.popBackStack()
                                scope.launch { snackbar.showSnackbar("账目已更新") }
                            }
                        }
                        composable("copy/{id}") { entry ->
                            val id = requireNotNull(entry.arguments?.getString("id"))
                            val model: EntryViewModel = viewModel(factory = remember(repository, id) {
                                viewModelFactory { initializer { EntryViewModel(repository, createSavedStateHandle(), null, copyFromId = id) } }
                            })
                            EntryScreen(model, onSavingChanged = { entrySaving = it }) { navigation.popBackStack(); scope.launch { snackbar.showSnackbar("新账目已保存") } }
                        }
                    }
                }
            }
        }
    }
}
