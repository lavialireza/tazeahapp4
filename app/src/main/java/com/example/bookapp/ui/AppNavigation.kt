package com.example.bookapp.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.bookapp.data.AppDatabase
import com.example.bookapp.data.Prefs
import com.example.bookapp.data.SectionEntity
import com.example.bookapp.data.TaziehRepository
import com.example.bookapp.data.syncLocalContentFiles
import com.example.bookapp.data.syncRemoteContent
import com.example.bookapp.ui.screens.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private const val ROUTE_SPLASH = "splash"
private const val ROUTE_ONBOARDING = "onboarding"
private const val ROUTE_LOGIN = "login"
private const val ROUTE_MAIN_MENU = "main_menu"
private const val ROUTE_SEARCH = "search"
private const val ROUTE_BOOKMARKS = "bookmarks"
private const val ROUTE_NOTES = "notes"
private const val ROUTE_MY_ROLE = "my_role"
private const val ROUTE_ALL_IMAGES = "all_images"
private const val ROUTE_REHEARSAL = "rehearsal/{roleId}/{roleTitle}"
private const val ROUTE_ABOUT = "about"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_VERSION = "version"
private const val ROUTE_CHANGELOG = "changelog"
private const val ROUTE_FIELDS = "fields"
private const val ROUTE_TAZIEHS = "taziehs/{fieldId}/{fieldTitle}"
private const val ROUTE_ROLES = "roles/{taziehId}/{taziehTitle}"
private const val ROUTE_SECTIONS = "sections/{roleId}/{roleTitle}"
private const val ROUTE_TAZIEH_INDEX = "tazieh_index/{taziehId}/{taziehTitle}"
private const val ROUTE_DIALOGUES = "dialogues/{taziehId}/{taziehTitle}"
private const val ROUTE_DIALOGUE_BUILDER = "dialogue_builder/{taziehId}/{taziehTitle}"
private const val ROUTE_DIALOGUE_READER = "dialogue_reader/{dialogueId}"
private const val ROUTE_TAZIEH_GALLERY = "tazieh_gallery/{taziehId}/{taziehTitle}"
private const val ROUTE_TEXT = "text/{sectionId}"
private const val ROUTE_TEXT_PAGER = "text_pager/{roleId}/{startIndex}"
private const val ROUTE_COMPARE = "compare/{roleAId}/{roleBId}"

@Composable
fun AppNavigation(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    autoDarkMode: Boolean,
    onAutoDarkModeChange: (Boolean) -> Unit,
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    themeChoice: String,
    onThemeChoiceChange: (String) -> Unit,
    fontChoice: String,
    onFontChoiceChange: (String) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    shortcutTarget: String?,
    deepLinkSectionId: Long? = null
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    // لایه‌ی Repository که ViewModelهای مسیرهای «زمینه/تعزیه/نقش/بخش» با آن کار می‌کنند
    // (رفته‌رفته بقیه‌ی مسیرها هم به همین الگو منتقل می‌شوند)
    val repository = remember(db) { TaziehRepository(context, db) }
    val navController: NavHostController = rememberNavController()

    LaunchedEffect(Unit) {
        // نوتیفیکیشن فقط برای کاربرانی که قبلاً برنامه را استفاده کرده‌اند نشان داده
        // می‌شود؛ در اولین نصب/اجرا محتوای اولیه «تازه» محسوب نمی‌شود
        val isReturningUser = Prefs.getProcessedContentFiles(context).isNotEmpty()
        val newFilesCount = syncLocalContentFiles(context, db)
        if (isReturningUser && newFilesCount > 0) {
            com.example.bookapp.data.showNewContentNotification(context, newFilesCount)
        }
    }

    // مقصد بعد از ورود: اگر از طریق لینک اشتراک‌گذاری یک بخش خاص باز شده باشد
    // اولویت با آن است؛ وگرنه اگر از میان‌بر آیکون باز شده باشد، به همان مقصد می‌رویم
    fun postLoginRoute(): String = when {
        deepLinkSectionId != null -> "text/$deepLinkSectionId"
        shortcutTarget == "search" -> ROUTE_SEARCH
        shortcutTarget == "notes" -> ROUTE_NOTES
        shortcutTarget == "bookmarks" -> ROUTE_BOOKMARKS
        else -> ROUTE_MAIN_MENU
    }

    // Activity با launchMode="singleTask" اجرا می‌شود، پس اگر برنامه از قبل باز باشد
    // و کاربر دوباره روی یک لینک اشتراک‌گذاری/میان‌بر بزند، همین AppNavigation composable
    // زنده می‌ماند و فقط deepLinkSectionId/shortcutTarget مقدار تازه می‌گیرند (بدون
    // Splash/Login از نو). این افکت مقصد تازه را مستقیماً navigate می‌کند، اما بار اول
    // (مقداردهی اولیه‌ی همین ورودی‌ها هنگام اولین ترکیب) را نادیده می‌گیرد چون آن حالت
    // از طریق postLoginRoute() در جریان عادی Splash -> Login مدیریت می‌شود.
    var initialDeepLinkHandled by remember { mutableStateOf(false) }
    LaunchedEffect(deepLinkSectionId, shortcutTarget) {
        if (!initialDeepLinkHandled) {
            initialDeepLinkHandled = true
            return@LaunchedEffect
        }
        when {
            deepLinkSectionId != null -> navController.navigate("text/$deepLinkSectionId") { launchSingleTop = true }
            shortcutTarget == "search" -> navController.navigate(ROUTE_SEARCH) { launchSingleTop = true }
            shortcutTarget == "notes" -> navController.navigate(ROUTE_NOTES) { launchSingleTop = true }
            shortcutTarget == "bookmarks" -> navController.navigate(ROUTE_BOOKMARKS) { launchSingleTop = true }
        }
    }

    // انیمیشن سریع و سبک بین صفحات (نه کند/سنگین)، تا هم نرم باشد و هم سرعت استفاده از برنامه افت نکند
    val navAnimDuration = 220
    NavHost(
        navController = navController,
        startDestination = ROUTE_SPLASH,
        enterTransition = {
            fadeIn(tween(navAnimDuration)) + slideInHorizontally(tween(navAnimDuration)) { it / 6 }
        },
        exitTransition = {
            fadeOut(tween(navAnimDuration)) + slideOutHorizontally(tween(navAnimDuration)) { -it / 6 }
        },
        popEnterTransition = {
            fadeIn(tween(navAnimDuration)) + slideInHorizontally(tween(navAnimDuration)) { -it / 6 }
        },
        popExitTransition = {
            fadeOut(tween(navAnimDuration)) + slideOutHorizontally(tween(navAnimDuration)) { it / 6 }
        }
    ) {

        composable(ROUTE_SPLASH) {
            SplashScreen(onFinished = {
                val next = if (Prefs.isOnboardingShown(context)) ROUTE_LOGIN else ROUTE_ONBOARDING
                navController.navigate(next) {
                    popUpTo(ROUTE_SPLASH) { inclusive = true }
                }
            })
        }

        composable(ROUTE_ONBOARDING) {
            OnboardingScreen(onFinished = {
                Prefs.setOnboardingShown(context)
                navController.navigate(ROUTE_LOGIN) {
                    popUpTo(ROUTE_ONBOARDING) { inclusive = true }
                }
            })
        }

        composable(ROUTE_LOGIN) {
            LoginScreen(onLoginSuccess = {
                navController.navigate(postLoginRoute()) {
                    popUpTo(ROUTE_LOGIN) { inclusive = true }
                }
            })
        }

        composable(ROUTE_MAIN_MENU) {
            val vm = com.example.bookapp.ui.viewmodel.rememberRepositoryViewModel(repository) {
                com.example.bookapp.ui.viewmodel.MainMenuViewModel(it)
            }
            LaunchedEffect(Unit) { vm.loadIfNeeded() }
            val ui by vm.state.collectAsState()

            MainMenuScreen(
                randomVerse = ui.randomVerse,
                recentItems = ui.recentItems,
                onOpenTaziehList = { navController.navigate(ROUTE_FIELDS) },
                onOpenSearch = { navController.navigate(ROUTE_SEARCH) },
                onOpenBookmarks = { navController.navigate(ROUTE_BOOKMARKS) },
                onOpenNotes = { navController.navigate(ROUTE_NOTES) },
                onOpenGallery = { navController.navigate(ROUTE_ALL_IMAGES) },
                onOpenMyRole = { navController.navigate(ROUTE_MY_ROLE) },
                onOpenAbout = { navController.navigate(ROUTE_ABOUT) },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                onOpenVersion = { navController.navigate(ROUTE_VERSION) },
                onOpenChangelog = { navController.navigate(ROUTE_CHANGELOG) },
                onItemClick = { result -> navController.navigate("text/${result.sectionId}") }
            )
        }

        composable(ROUTE_SEARCH) {
            val vm = com.example.bookapp.ui.viewmodel.rememberRepositoryViewModel(repository) {
                com.example.bookapp.ui.viewmodel.SearchViewModel(it)
            }
            LaunchedEffect(Unit) { vm.loadIfNeeded() }
            val ui by vm.state.collectAsState()

            SearchScreen(
                fields = ui.fields,
                allTaziehs = ui.allTaziehs,
                onSearch = { query, fieldId, taziehId -> vm.search(query, fieldId, taziehId) },
                onResultClick = { result -> navController.navigate("text/${result.sectionId}") },
                onSearchDialogues = { query -> vm.searchDialogues(query) },
                onDialogueResultClick = { d -> navController.navigate("dialogue_reader/${d.dialogueId}") },
                isBookmarked = { id -> id in ui.bookmarkedIds },
                onToggleBookmark = { id -> vm.toggleBookmark(id) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_BOOKMARKS) {
            val vm = com.example.bookapp.ui.viewmodel.rememberRepositoryViewModel(repository) {
                com.example.bookapp.ui.viewmodel.BookmarksViewModel(it)
            }
            LaunchedEffect(Unit) { vm.load() }
            val ui by vm.state.collectAsState()

            BookmarksScreen(
                items = ui.items,
                onItemClick = { result -> navController.navigate("text/${result.sectionId}") },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_NOTES) {
            val vm = com.example.bookapp.ui.viewmodel.rememberRepositoryViewModel(repository) {
                com.example.bookapp.ui.viewmodel.NotesViewModel(it)
            }
            LaunchedEffect(Unit) { vm.load() }
            val ui by vm.state.collectAsState()

            NotesScreen(
                notes = ui.notes,
                onAddNote = { title, content -> vm.addNote(title, content) },
                onDeleteNote = { id -> vm.deleteNote(id) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_MY_ROLE) {
            val vm = com.example.bookapp.ui.viewmodel.rememberRepositoryViewModel(repository) {
                com.example.bookapp.ui.viewmodel.MyRoleViewModel(it)
            }
            LaunchedEffect(Unit) { vm.load() }
            val ui by vm.state.collectAsState()

            MyRoleScreen(
                items = ui.items,
                onRead = { item ->
                    navController.navigate("sections/${item.roleId}/${item.roleTitle}")
                },
                onRehearse = { item ->
                    navController.navigate("rehearsal/${item.roleId}/${item.roleTitle}")
                },
                onExportPdf = { item -> vm.exportPdf(context, item) },
                onRemove = { item -> vm.remove(item) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_ALL_IMAGES) {
            val vm = com.example.bookapp.ui.viewmodel.rememberRepositoryViewModel(repository) {
                com.example.bookapp.ui.viewmodel.AllImagesViewModel(it)
            }
            LaunchedEffect(Unit) { vm.load() }
            val ui by vm.state.collectAsState()

            AllImagesGalleryScreen(
                images = ui.images,
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_REHEARSAL) { backStackEntry ->
            val roleId = backStackEntry.arguments?.getString("roleId")?.toLongOrNull() ?: 0L
            val roleTitle = backStackEntry.arguments?.getString("roleTitle") ?: ""
            val vm = com.example.bookapp.ui.viewmodel.rememberRepositoryViewModel(repository) {
                com.example.bookapp.ui.viewmodel.RehearsalViewModel(it)
            }
            LaunchedEffect(roleId) { vm.loadIfNeeded(roleId) }
            val ui by vm.state.collectAsState()

            RehearsalScreen(
                roleTitle = roleTitle,
                sections = ui.sections,
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_ABOUT) {
            val vm = com.example.bookapp.ui.viewmodel.rememberRepositoryViewModel(repository) {
                com.example.bookapp.ui.viewmodel.AboutViewModel(it)
            }
            LaunchedEffect(Unit) { vm.loadIfNeeded() }
            val ui by vm.state.collectAsState()

            AboutScreen(
                fieldsCount = ui.fieldsCount,
                taziehsCount = ui.taziehsCount,
                rolesCount = ui.rolesCount,
                sectionsCount = ui.sectionsCount,
                readCount = ui.readCount,
                streakDays = ui.streakDays,
                activeDaysLast14 = ui.activeDaysLast14,
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                darkMode = darkMode,
                onDarkModeChange = onDarkModeChange,
                autoDarkMode = autoDarkMode,
                onAutoDarkModeChange = onAutoDarkModeChange,
                fontScale = fontScale,
                onFontScaleChange = onFontScaleChange,
                fontChoice = fontChoice,
                onFontChoiceChange = onFontChoiceChange,
                themeChoice = themeChoice,
                onThemeChoiceChange = onThemeChoiceChange,
                keepScreenOn = keepScreenOn,
                onKeepScreenOnChange = onKeepScreenOnChange,
                onSyncContent = { syncRemoteContent(db) },
                db = db,
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_VERSION) { VersionScreen(onBack = { navController.popBackStack() }) }

        composable(ROUTE_CHANGELOG) {
            ChangelogScreen(
                entries = listOf(
                    ChangelogEntry("جدید", listOf(
                        "پشتیبان‌گیری کامل (یادداشت، بوکمارک، پاورقی، نقش من، گفتگو) با امکان ذخیره در فضای ابری یا حافظه گوشی و بازیابی",
                        "گزارش اشکال محتوا از داخل هر متن",
                        "تصحیح رنگ تم طلایی در حالت روشن",
                        "امکان روشن/خاموش‌کردن قفل صفحه از تنظیمات",
                        "پس‌زمینه متفاوت برای نقش دوم در مقایسه و گفتگو"
                    )),
                    ChangelogEntry("نسخه‌های قبلی", listOf(
                        "گالری تصاویر (اختصاصی هر تعزیه + گالری عمومی در منوی اصلی)",
                        "صدای واقعی برای بخش‌ها",
                        "گفتگوهای چندنقشی (مثل شمر و عباس)",
                        "فهرست تعزیه با ترتیب قابل ویرایش",
                        "حالت تمرین و نقش من",
                        "پاورقی برای واژه‌ها و توضیحات"
                    ))
                ),
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_FIELDS) {
            val vm = com.example.bookapp.ui.viewmodel.rememberRepositoryViewModel(repository) {
                com.example.bookapp.ui.viewmodel.FieldsViewModel(it)
            }
            LaunchedEffect(Unit) { vm.loadIfNeeded() }
            val ui by vm.state.collectAsState()

            GenericListScreen(
                screenTitle = "زمینه‌ها",
                items = ui.items,
                loading = ui.loading,
                onItemClick = { navController.navigate("taziehs/${it.id}/${it.title}") },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_TAZIEHS) { backStackEntry ->
            val fieldId = backStackEntry.arguments?.getString("fieldId")?.toLongOrNull() ?: 0L
            val fieldTitle = backStackEntry.arguments?.getString("fieldTitle") ?: ""
            val vm = com.example.bookapp.ui.viewmodel.rememberRepositoryViewModel(repository) {
                com.example.bookapp.ui.viewmodel.TaziehsViewModel(it)
            }
            LaunchedEffect(fieldId) { vm.loadIfNeeded(fieldId) }
            val ui by vm.state.collectAsState()

            GenericListScreen(
                screenTitle = fieldTitle,
                items = ui.items,
                loading = ui.loading,
                onItemClick = { navController.navigate("roles/${it.id}/${it.title}") },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_ROLES) { backStackEntry ->
            val taziehId = backStackEntry.arguments?.getString("taziehId")?.toLongOrNull() ?: 0L
            val taziehTitle = backStackEntry.arguments?.getString("taziehTitle") ?: ""
            val vm = com.example.bookapp.ui.viewmodel.rememberRepositoryViewModel(repository) {
                com.example.bookapp.ui.viewmodel.RolesViewModel(it)
            }
            val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
            val scope = androidx.compose.runtime.rememberCoroutineScope()

            LaunchedEffect(taziehId) { vm.loadIfNeeded(taziehId) }
            LaunchedEffect(vm) {
                vm.events.collect { event ->
                    when (event) {
                        is com.example.bookapp.ui.viewmodel.RolesEvent.NavigateToCompare ->
                            navController.navigate("compare/${event.roleAId}/${event.roleBId}")
                        is com.example.bookapp.ui.viewmodel.RolesEvent.ShowMessage ->
                            scope.launch { snackbarHostState.showSnackbar(event.text) }
                    }
                }
            }

            val ui by vm.state.collectAsState()

            GenericListScreen(
                screenTitle = when {
                    ui.compareMode -> "دو نقش را انتخاب کنید"
                    ui.selectMyRoleMode -> "نقش خودتان را انتخاب کنید"
                    else -> taziehTitle
                },
                items = ui.items,
                loading = ui.loading,
                onItemClick = { navController.navigate("sections/${it.id}/${it.title}") },
                onBack = {
                    if (ui.compareMode || ui.selectMyRoleMode) vm.cancelSelectionMode()
                    else navController.popBackStack()
                },
                selectedIds = when {
                    ui.compareMode -> ui.selectedIds
                    ui.myRoleId != null -> setOf(ui.myRoleId!!)
                    else -> emptySet()
                },
                onToggleSelect = if (ui.compareMode || ui.selectMyRoleMode) {
                    { item: ListItemData -> vm.onToggleSelect(taziehId, item) }
                } else null,
                topBarAction = {
                    var menuExpanded by remember { mutableStateOf(false) }
                    androidx.compose.foundation.layout.Row {
                        androidx.compose.material3.TextButton(onClick = { vm.toggleSelectMyRoleMode() }) {
                            androidx.compose.material3.Text(if (ui.selectMyRoleMode) "لغو" else "نقش من")
                        }
                        androidx.compose.material3.TextButton(onClick = { vm.toggleCompareMode() }) {
                            androidx.compose.material3.Text(if (ui.compareMode) "لغو" else "مقایسه")
                        }
                        androidx.compose.material3.IconButton(onClick = { menuExpanded = true }) {
                            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.MoreVert, contentDescription = "بیشتر")
                        }
                        androidx.compose.material3.DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { androidx.compose.material3.Text("فهرست") },
                                onClick = {
                                    menuExpanded = false
                                    navController.navigate("tazieh_index/$taziehId/$taziehTitle")
                                }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { androidx.compose.material3.Text("گفتگوها") },
                                onClick = {
                                    menuExpanded = false
                                    navController.navigate("dialogues/$taziehId/$taziehTitle")
                                }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { androidx.compose.material3.Text("تصاویر") },
                                onClick = {
                                    menuExpanded = false
                                    navController.navigate("tazieh_gallery/$taziehId/$taziehTitle")
                                }
                            )
                        }
                    }
                },
                snackbarHostState = snackbarHostState
            )
        }

        composable(ROUTE_TAZIEH_INDEX) { backStackEntry ->
            val taziehId = backStackEntry.arguments?.getString("taziehId")?.toLongOrNull() ?: 0L
            val taziehTitle = backStackEntry.arguments?.getString("taziehTitle") ?: ""
            val vm = com.example.bookapp.ui.viewmodel.rememberRepositoryViewModel(repository) {
                com.example.bookapp.ui.viewmodel.TaziehIndexViewModel(it)
            }
            LaunchedEffect(taziehId) { vm.load(taziehId) }
            val ui by vm.state.collectAsState()

            TaziehIndexScreen(
                taziehTitle = taziehTitle,
                items = ui.items,
                onItemClick = { item -> navController.navigate("text_pager/${item.roleId}/0") },
                onExportPdf = { vm.exportPdf(context, taziehId, taziehTitle) },
                onRename = { item, newTitle -> vm.rename(taziehId, item, newTitle) },
                onMove = { index, direction -> vm.move(taziehId, index, direction) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_DIALOGUES) { backStackEntry ->
            val taziehId = backStackEntry.arguments?.getString("taziehId")?.toLongOrNull() ?: 0L
            val taziehTitle = backStackEntry.arguments?.getString("taziehTitle") ?: ""
            var dialogues by remember { mutableStateOf(listOf<DialogueSummary>()) }
            val scope = androidx.compose.runtime.rememberCoroutineScope()

            suspend fun reloadDialogues() {
                dialogues = db.dialogueDao().getByTazieh(taziehId).map { d ->
                    DialogueSummary(d.id, d.title, db.dialogueTurnDao().getByDialogue(d.id).size)
                }
            }
            LaunchedEffect(taziehId) { reloadDialogues() }

            DialoguesScreen(
                taziehTitle = taziehTitle,
                dialogues = dialogues,
                onOpenDialogue = { d -> navController.navigate("dialogue_reader/${d.id}") },
                onDeleteDialogue = { d ->
                    scope.launch {
                        db.dialogueDao().delete(d.id)
                        reloadDialogues()
                    }
                },
                onCreateNew = { navController.navigate("dialogue_builder/$taziehId/$taziehTitle") },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_DIALOGUE_BUILDER) { backStackEntry ->
            val taziehId = backStackEntry.arguments?.getString("taziehId")?.toLongOrNull() ?: 0L
            val taziehTitle = backStackEntry.arguments?.getString("taziehTitle") ?: ""
            var allSections by remember { mutableStateOf(listOf<SectionPickerItem>()) }
            val scope = androidx.compose.runtime.rememberCoroutineScope()

            LaunchedEffect(taziehId) {
                val roles = db.roleDao().getByTazieh(taziehId)
                allSections = roles.flatMap { role ->
                    db.sectionDao().getByRole(role.id).map { section ->
                        SectionPickerItem(section.id, role.title, section.title)
                    }
                }
            }

            DialogueBuilderScreen(
                allSections = allSections,
                onSave = { title, orderedSectionIds ->
                    scope.launch {
                        val dialogueId = db.dialogueDao().insert(com.example.bookapp.data.DialogueEntity(taziehId = taziehId, title = title))
                        orderedSectionIds.forEachIndexed { index, sectionId ->
                            db.dialogueTurnDao().insert(
                                com.example.bookapp.data.DialogueTurnEntity(dialogueId = dialogueId, sectionId = sectionId, orderIndex = index)
                            )
                        }
                        navController.popBackStack()
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_DIALOGUE_READER) { backStackEntry ->
            val dialogueId = backStackEntry.arguments?.getString("dialogueId")?.toLongOrNull() ?: 0L
            var dialogueTitle by remember { mutableStateOf("") }
            var turns by remember { mutableStateOf(listOf<DialogueTurnDisplay>()) }
            val scope = androidx.compose.runtime.rememberCoroutineScope()

            suspend fun reloadTurns() {
                val dialogue = db.dialogueDao().getById(dialogueId)
                dialogueTitle = dialogue.title
                val turnEntities = db.dialogueTurnDao().getByDialogue(dialogueId)
                turns = turnEntities.map { turn ->
                    val section = db.sectionDao().getById(turn.sectionId)
                    val role = db.roleDao().getById(section.roleId)
                    DialogueTurnDisplay(
                        turnId = turn.id,
                        sectionId = section.id,
                        roleTitle = role.title,
                        sectionTitle = section.title,
                        content = section.content
                    )
                }
            }
            LaunchedEffect(dialogueId) { reloadTurns() }

            DialogueReaderScreen(
                dialogueTitle = dialogueTitle,
                turns = turns,
                onMoveTurn = { index, direction ->
                    scope.launch {
                        val turnEntities = db.dialogueTurnDao().getByDialogue(dialogueId)
                        val targetIndex = index + direction
                        if (targetIndex in turnEntities.indices) {
                            val a = turnEntities[index]
                            val b = turnEntities[targetIndex]
                            db.dialogueTurnDao().updateOrderIndex(a.id, b.orderIndex)
                            db.dialogueTurnDao().updateOrderIndex(b.id, a.orderIndex)
                            reloadTurns()
                        }
                    }
                },
                onDeleteTurn = { turn ->
                    scope.launch {
                        db.dialogueTurnDao().deleteTurn(turn.turnId)
                        reloadTurns()
                    }
                },
                onExportPdf = {
                    scope.launch {
                        val triples = turns.map { Triple(it.roleTitle, it.sectionTitle, it.content) }
                        com.example.bookapp.data.exportDialogueToPdf(context, dialogueTitle, triples)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_TAZIEH_GALLERY) { backStackEntry ->
            val taziehId = backStackEntry.arguments?.getString("taziehId")?.toLongOrNull() ?: 0L
            val taziehTitle = backStackEntry.arguments?.getString("taziehTitle") ?: ""
            var images by remember { mutableStateOf(listOf<TaziehImageItem>()) }
            val scope = androidx.compose.runtime.rememberCoroutineScope()

            suspend fun reloadImages() {
                images = db.taziehImageDao().getByTazieh(taziehId).map {
                    TaziehImageItem(it.id, it.filePath, it.caption)
                }
            }
            LaunchedEffect(taziehId) { reloadImages() }

            TaziehGalleryScreen(
                taziehTitle = taziehTitle,
                images = images,
                onAddImage = { uri ->
                    scope.launch {
                        val path = com.example.bookapp.data.copyImageToAppStorage(context, uri)
                        if (path != null) {
                            db.taziehImageDao().insert(com.example.bookapp.data.TaziehImageEntity(taziehId = taziehId, filePath = path))
                            reloadImages()
                        }
                    }
                },
                onDeleteImage = { image ->
                    scope.launch {
                        db.taziehImageDao().delete(image.id)
                        com.example.bookapp.data.deleteImageFromAppStorage(image.filePath)
                        reloadImages()
                    }
                },
                onUpdateCaption = { image, caption ->
                    scope.launch {
                        db.taziehImageDao().updateCaption(image.id, caption)
                        reloadImages()
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_COMPARE) { backStackEntry ->
            val roleAId = backStackEntry.arguments?.getString("roleAId")?.toLongOrNull() ?: 0L
            val roleBId = backStackEntry.arguments?.getString("roleBId")?.toLongOrNull() ?: 0L
            var roleATitle by remember { mutableStateOf("") }
            var roleBTitle by remember { mutableStateOf("") }
            var roleASections by remember { mutableStateOf(listOf<SectionEntity>()) }
            var roleBSections by remember { mutableStateOf(listOf<SectionEntity>()) }
            LaunchedEffect(roleAId, roleBId) {
                roleASections = db.sectionDao().getByRole(roleAId)
                roleBSections = db.sectionDao().getByRole(roleBId)
                roleATitle = db.roleDao().getById(roleAId).title
                roleBTitle = db.roleDao().getById(roleBId).title
            }
            CompareScreen(
                roleATitle = roleATitle.ifBlank { "نقش اول" },
                roleASections = roleASections,
                roleBTitle = roleBTitle.ifBlank { "نقش دوم" },
                roleBSections = roleBSections,
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_SECTIONS) { backStackEntry ->
            val roleId = backStackEntry.arguments?.getString("roleId")?.toLongOrNull() ?: 0L
            val roleTitle = backStackEntry.arguments?.getString("roleTitle") ?: ""
            val vm = com.example.bookapp.ui.viewmodel.rememberRepositoryViewModel(repository) {
                com.example.bookapp.ui.viewmodel.SectionsViewModel(it)
            }
            LaunchedEffect(roleId) { vm.loadIfNeeded(roleId) }
            val ui by vm.state.collectAsState()

            GenericListScreen(
                screenTitle = roleTitle,
                items = ui.items,
                loading = ui.loading,
                onItemClick = { clicked ->
                    val index = ui.items.indexOfFirst { it.id == clicked.id }.coerceAtLeast(0)
                    navController.navigate("text_pager/$roleId/$index")
                },
                onBack = { navController.popBackStack() },
                floatingAction = {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                        androidx.compose.material3.ExtendedFloatingActionButton(
                            text = { androidx.compose.material3.Text("حالت تمرین") },
                            icon = { androidx.compose.material3.Icon(Icons.Filled.School, contentDescription = null) },
                            onClick = { navController.navigate("rehearsal/$roleId/$roleTitle") }
                        )
                        Spacer(Modifier.height(10.dp))
                        androidx.compose.material3.ExtendedFloatingActionButton(
                            text = { androidx.compose.material3.Text("خروجی PDF") },
                            icon = { androidx.compose.material3.Icon(Icons.Filled.Share, contentDescription = null) },
                            onClick = { vm.exportPdf(context, roleId, roleTitle) }
                        )
                    }
                }
            )
        }

        composable(ROUTE_TEXT_PAGER) { backStackEntry ->
            val roleId = backStackEntry.arguments?.getString("roleId")?.toLongOrNull() ?: 0L
            val startIndex = backStackEntry.arguments?.getString("startIndex")?.toIntOrNull() ?: 0
            var sections by remember { mutableStateOf(listOf<SectionEntity>()) }
            var bookmarkVersion by remember { mutableIntStateOf(0) }
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            LaunchedEffect(roleId) {
                sections = db.sectionDao().getByRole(roleId)
            }
            if (sections.isNotEmpty()) {
                TextPagerScreen(
                    sections = sections,
                    startIndex = startIndex,
                    isBookmarked = { id -> bookmarkVersion.let { Prefs.isBookmarked(context, id) } },
                    onToggleBookmark = { id ->
                        Prefs.toggleBookmark(context, id)
                        bookmarkVersion++
                    },
                    onPageShown = { id ->
                        Prefs.addRecent(context, id)
                        Prefs.markSectionRead(context, id)
                    },
                    onOpenSearch = { navController.navigate(ROUTE_SEARCH) },
                    onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                    onAttachAudio = { sectionId, uri ->
                        scope.launch {
                            val path = com.example.bookapp.data.copyAudioToAppStorage(context, uri)
                            if (path != null) {
                                db.sectionDao().updateAudioUrl(sectionId, path)
                                sections = db.sectionDao().getByRole(roleId)
                            }
                        }
                    },
                    onRemoveAudio = { sectionId ->
                        scope.launch {
                            sections.find { it.id == sectionId }?.audioUrl?.let {
                                com.example.bookapp.data.deleteAudioFromAppStorage(it)
                            }
                            db.sectionDao().updateAudioUrl(sectionId, null)
                            sections = db.sectionDao().getByRole(roleId)
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(ROUTE_TEXT) { backStackEntry ->
            val sectionId = backStackEntry.arguments?.getString("sectionId")?.toLongOrNull() ?: 0L
            var title by remember { mutableStateOf("") }
            var content by remember { mutableStateOf("") }
            var bookmarked by remember { mutableStateOf(Prefs.isBookmarked(context, sectionId)) }
            var relatedSections by remember { mutableStateOf(listOf<com.example.bookapp.data.SearchResult>()) }
            var sectionAudioUrl by remember { mutableStateOf<String?>(null) }
            var footnotes by remember { mutableStateOf(listOf<com.example.bookapp.data.FootnoteEntity>()) }
            var siblingSections by remember { mutableStateOf(listOf<com.example.bookapp.data.SectionEntity>()) }
            var siblingIndex by remember { mutableStateOf(-1) }
            val scope = androidx.compose.runtime.rememberCoroutineScope()

            suspend fun reloadFootnotes() {
                footnotes = db.footnoteDao().getBySection(sectionId)
            }

            LaunchedEffect(sectionId) {
                val section = db.sectionDao().getById(sectionId)
                title = section.title
                content = section.content
                bookmarked = Prefs.isBookmarked(context, sectionId)
                sectionAudioUrl = section.audioUrl
                Prefs.addRecent(context, sectionId)
                Prefs.markSectionRead(context, sectionId)
                relatedSections = db.searchDao().getRelatedByTitle(section.title, sectionId)
                reloadFootnotes()
                siblingSections = db.sectionDao().getByRole(section.roleId)
                siblingIndex = siblingSections.indexOfFirst { it.id == sectionId }
            }
            TextScreen(
                title = title,
                content = content,
                isBookmarked = bookmarked,
                onToggleBookmark = {
                    bookmarked = Prefs.toggleBookmark(context, sectionId)
                },
                sectionId = sectionId,
                audioUrl = sectionAudioUrl,
                relatedSections = relatedSections,
                onRelatedClick = { related -> navController.navigate("text/${related.sectionId}") },
                footnotes = footnotes,
                onAddFootnote = { term, explanation ->
                    scope.launch {
                        db.footnoteDao().insert(com.example.bookapp.data.FootnoteEntity(sectionId = sectionId, term = term, explanation = explanation))
                        reloadFootnotes()
                    }
                },
                onEditFootnote = { fn, term, explanation ->
                    scope.launch {
                        db.footnoteDao().update(fn.copy(term = term, explanation = explanation))
                        reloadFootnotes()
                    }
                },
                onDeleteFootnote = { fn ->
                    scope.launch {
                        db.footnoteDao().delete(fn.id)
                        reloadFootnotes()
                    }
                },
                onOpenSearch = { navController.navigate(ROUTE_SEARCH) },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                hasPrevSection = siblingIndex > 0,
                hasNextSection = siblingIndex in 0 until siblingSections.size - 1,
                onPrevSection = {
                    if (siblingIndex > 0) navController.navigate("text/${siblingSections[siblingIndex - 1].id}")
                },
                onNextSection = {
                    if (siblingIndex in 0 until siblingSections.size - 1) navController.navigate("text/${siblingSections[siblingIndex + 1].id}")
                },
                onAttachAudio = { uri ->
                    scope.launch {
                        val path = com.example.bookapp.data.copyAudioToAppStorage(context, uri)
                        if (path != null) {
                            db.sectionDao().updateAudioUrl(sectionId, path)
                            sectionAudioUrl = path
                        }
                    }
                },
                onRemoveAudio = {
                    scope.launch {
                        sectionAudioUrl?.let { com.example.bookapp.data.deleteAudioFromAppStorage(it) }
                        db.sectionDao().updateAudioUrl(sectionId, null)
                        sectionAudioUrl = null
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
