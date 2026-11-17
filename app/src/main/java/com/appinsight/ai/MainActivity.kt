package com.appinsight.ai

import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.appinsight.ai.ui.theme.AppInsightTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import java.text.DateFormat
import java.util.Date

private enum class Screen(val title: String, val icon: String) {
    DASH("Resumen", "◈"), REVIEWS("Reseñas", "★"), ISSUES("Temas", "!"),
    INSIGHTS("Acciones", "✦"), SEARCH("Buscar", "⌕"), PORTFOLIO("Mis apps", "▤")
}
private val mainScreens = Screen.entries.filterNot { it == Screen.PORTFOLIO }

private enum class AppLanguage(val code: String, val label: String) {
    ES("ES", "Español"), EN("EN", "English"), PT("PT", "Português")
}
private val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.ES }
@Composable private fun tr(es: String, en: String, pt: String): String = when (LocalAppLanguage.current) {
    AppLanguage.ES -> es; AppLanguage.EN -> en; AppLanguage.PT -> pt
}
@Composable private fun screenTitle(screen: Screen) = when (screen) {
    Screen.DASH -> tr("Resumen", "Overview", "Resumo")
    Screen.REVIEWS -> tr("Reseñas", "Reviews", "Avaliações")
    Screen.ISSUES -> tr("Temas", "Topics", "Temas")
    Screen.INSIGHTS -> tr("Acciones", "Actions", "Ações")
    Screen.SEARCH -> tr("Buscar", "Search", "Buscar")
    Screen.PORTFOLIO -> tr("Mis apps", "My apps", "Meus apps")
}
@Composable private fun topicLabel(topic: String): String = when (topic.lowercase()) {
    "inicio de sesión" -> tr("Inicio de sesión", "Login", "Login")
    "rendimiento" -> tr("Rendimiento", "Performance", "Desempenho")
    "cierres inesperados" -> tr("Cierres inesperados", "Unexpected crashes", "Fechamentos inesperados")
    "notificaciones" -> tr("Notificaciones", "Notifications", "Notificações")
    "conectividad" -> tr("Conectividad", "Connectivity", "Conectividade")
    "pagos" -> tr("Pagos", "Payments", "Pagamentos")
    else -> topic.replaceFirstChar { it.uppercase() }
}
@Composable private fun reviewOptionLabel(option: String): String = when (option) {
    "all" -> tr("Todas", "All", "Todas")
    "detailed" -> tr("Más detalladas", "Most detailed", "Mais detalhadas")
    "negative" -> tr("Negativas", "Negative", "Negativas")
    "positive" -> tr("Positivas", "Positive", "Positivas")
    "recent" -> tr("Más recientes", "Most recent", "Mais recentes")
    "best" -> tr("Mejor calificadas", "Highest rated", "Melhor avaliadas")
    "worst" -> tr("Peor calificadas", "Lowest rated", "Pior avaliadas")
    else -> "${option.removePrefix("star")} ${tr("estrella", "star", "estrela")}${if (option == "star1") "" else "s"}"
}

private data class PlayMarket(val country: String, val language: String, val label: String, val flag: String)
private val playMarkets = listOf(
    PlayMarket("ar", "es", "Argentina", "🇦🇷"), PlayMarket("mx", "es", "México", "🇲🇽"),
    PlayMarket("cl", "es", "Chile", "🇨🇱"), PlayMarket("co", "es", "Colombia", "🇨🇴"),
    PlayMarket("pe", "es", "Perú", "🇵🇪"), PlayMarket("br", "pt", "Brasil", "🇧🇷"),
    PlayMarket("es", "es", "España", "🇪🇸"), PlayMarket("us", "en", "Estados Unidos", "🇺🇸"),
    PlayMarket("ca", "en", "Canadá", "🇨🇦"), PlayMarket("gb", "en", "Reino Unido", "🇬🇧"),
    PlayMarket("fr", "fr", "Francia", "🇫🇷"), PlayMarket("de", "de", "Alemania", "🇩🇪"),
    PlayMarket("it", "it", "Italia", "🇮🇹"), PlayMarket("in", "en", "India", "🇮🇳"),
    PlayMarket("jp", "ja", "Japón", "🇯🇵"), PlayMarket("au", "en", "Australia", "🇦🇺"),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) { super.onCreate(state); setContent { App() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun App() {
    val pager = rememberPagerState(initialPage = mainScreens.indexOf(Screen.SEARCH), pageCount = { mainScreens.size })
    var portfolioOpen by rememberSaveable { mutableStateOf(false) }
    val navigationScope = rememberCoroutineScope()
    fun goTo(screen: Screen) {
        portfolioOpen = false
        navigationScope.launch { pager.animateScrollToPage(mainScreens.indexOf(screen)) }
    }
    var dark by rememberSaveable { mutableStateOf(true) }
    var selected by remember { mutableStateOf<PlayApp?>(null) }
    var job by remember { mutableStateOf<ScrapeJob?>(null) }
    var insight by remember { mutableStateOf<Insights?>(null) }
    var reviews by remember { mutableStateOf<List<RealReview>>(emptyList()) }
    var activeMarket by remember { mutableStateOf(playMarkets.first()) }
    var activeReviewLimit by remember { mutableIntStateOf(50) }
    var activeReviewLanguages by remember { mutableStateOf<List<String>?>(null) }
    var userPlan by remember { mutableStateOf("free") }
    var planRefreshTick by remember { mutableIntStateOf(0) }
    var creditsRemaining by remember { mutableIntStateOf(3) }
    var creditsLimit by remember { mutableIntStateOf(3) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var refreshingTrend by remember { mutableStateOf(false) }
    var refreshError by remember { mutableStateOf(false) }
    var cacheError by remember { mutableStateOf(false) }
    var savedAnalyses by remember { mutableStateOf<List<SavedAnalysis>>(emptyList()) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var showAccountDetails by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var deletingAccount by remember { mutableStateOf(false) }
    var accountDeletionError by remember { mutableStateOf<String?>(null) }
    var storeComparison by remember { mutableStateOf<ComparisonResult?>(null) }
    var selectedComparisonCountry by remember { mutableStateOf<String?>(null) }
    fun openSaved(analysis: SavedAnalysis) {
        storeComparison = null
        selectedComparisonCountry = null
        selected = analysis.app
        job = null
        insight = analysis.insights
        reviews = analysis.reviews
        activeMarket = playMarkets.firstOrNull { it.country == analysis.marketCountry } ?: playMarkets.first()
        activeReviewLimit = analysis.maxReviews
        activeReviewLanguages = analysis.reviewLanguages
        refreshError = false
        goTo(Screen.DASH)
    }
    val context = LocalContext.current
    LaunchedEffect(Unit) { MobileAds.initialize(context) }
    val preferences = remember { context.getSharedPreferences("appinsight_preferences", Context.MODE_PRIVATE) }
    var showIntro by rememberSaveable { mutableStateOf(!preferences.getBoolean("intro_seen", false)) }
    var appLanguageCode by rememberSaveable { mutableStateOf(preferences.getString("app_language", "ES") ?: "ES") }
    val appLanguage = AppLanguage.entries.firstOrNull { it.code == appLanguageCode } ?: AppLanguage.ES
    var favorites by remember { mutableStateOf(preferences.getStringSet("favorite_searches", emptySet()).orEmpty().toList()) }
    fun saveFavorite(name: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        favorites = (listOf(cleanName) + favorites.filterNot { it.equals(cleanName, ignoreCase = true) }).take(8)
        preferences.edit().putStringSet("favorite_searches", favorites.toSet()).apply()
    }
    val firebaseAuth = remember { FirebaseAuth.getInstance() }
    val credentialManager = remember { CredentialManager.create(context) }
    val googleClientId = stringResource(com.appinsight.ai.R.string.default_web_client_id)
    var firebaseUser by remember { mutableStateOf(firebaseAuth.currentUser) }
    var emailVerified by remember { mutableStateOf(firebaseAuth.currentUser?.isEmailVerified == true) }
    DisposableEffect(firebaseAuth) {
        var previousUid = firebaseAuth.currentUser?.uid
        val listener = FirebaseAuth.AuthStateListener {
            if (previousUid != it.currentUser?.uid) {
                previousUid = it.currentUser?.uid
                userPlan = "free"
                creditsRemaining = 3; creditsLimit = 3
                selected = null; job = null; insight = null; reviews = emptyList()
                storeComparison = null; selectedComparisonCountry = null
                savedAnalyses = emptyList(); cacheError = false; refreshError = false
                portfolioOpen = false
            }
            firebaseUser = it.currentUser
            emailVerified = it.currentUser?.isEmailVerified == true
        }
        firebaseAuth.addAuthStateListener(listener)
        onDispose { firebaseAuth.removeAuthStateListener(listener) }
    }
    val api = remember(firebaseAuth) {
        ApiClient(AppConfig.backendBaseUrl) {
            // Fuerza la renovación para que email_verified se refleje justo después de confirmar.
            firebaseAuth.currentUser?.getIdToken(true)?.await()?.token
        }
    }
    DisposableEffect(context) {
        val activity = context as ComponentActivity
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) planRefreshTick++ }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(firebaseUser?.uid, emailVerified, planRefreshTick) {
        if (firebaseUser != null && emailVerified) runCatching { api.profile() }.onSuccess {
            userPlan = it.name
            creditsRemaining = it.creditsRemaining
            creditsLimit = it.creditsLimit
        }
        else userPlan = "free"
    }
    LaunchedEffect(firebaseUser?.uid, emailVerified) {
        val uid = firebaseUser?.uid ?: return@LaunchedEffect
        if (!emailVerified) return@LaunchedEffect
        runCatching { AnalysisCache.load(context, uid) }
            .onSuccess { loaded ->
                if (firebaseAuth.currentUser?.uid == uid) {
                    savedAnalyses = loaded
                    if (selected == null) loaded.firstOrNull()?.let(::openSaved)
                }
            }
            .onFailure { cacheError = true }
    }
    LaunchedEffect(refreshTick) {
        val selectedApp = selected
        if (refreshTick > 0 && selectedApp != null) {
            refreshingTrend = true
            refreshError = false
            runCatching { api.analyze(selectedApp, 30, activeMarket.country, activeMarket.language, activeReviewLimit, activeReviewLanguages) }.onSuccess { result ->
                if (firebaseAuth.currentUser?.uid != firebaseUser?.uid || selected?.id != selectedApp.id) return@onSuccess
                job = result.job; insight = result.insights; reviews = result.reviews
                result.job.creditsRemaining?.let { creditsRemaining = it }
                val uid = firebaseUser?.uid ?: return@onSuccess
                val saved = SavedAnalysis(selectedApp, result.insights, result.reviews, activeMarket.country, activeReviewLimit, activeReviewLanguages, System.currentTimeMillis())
                runCatching { AnalysisCache.save(context, uid, saved) }
                    .onSuccess { savedAnalyses = it; cacheError = false }
                    .onFailure { cacheError = true }
            }.onFailure { refreshError = true }
            refreshingTrend = false
        }
    }
    AppInsightTheme(dark) { CompositionLocalProvider(LocalAppLanguage provides appLanguage) {
        if (showIntro) {
            IntroScreen {
                preferences.edit().putBoolean("intro_seen", true).apply()
                showIntro = false
            }
            return@CompositionLocalProvider
        }
        if (firebaseUser == null) {
            AuthScreen(firebaseAuth) {
                firebaseUser = it
                emailVerified = it.isEmailVerified
            }
            return@CompositionLocalProvider
        }
        if (!emailVerified) {
            EmailVerificationScreen(firebaseAuth, firebaseUser?.email.orEmpty()) { emailVerified = true }
            return@CompositionLocalProvider
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    navigationIcon = { BrandMark() },
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(tr("Centro de inteligencia", "Intelligence center", "Centro de inteligência"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(selected?.let { "${tr("APP ACTIVA", "ACTIVE APP", "APP ATIVO")} · ${it.title.uppercase()}" } ?: tr("RESEÑAS · SEÑALES · DECISIONES", "REVIEWS · SIGNALS · DECISIONS", "AVALIAÇÕES · SINAIS · DECISÕES"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            val next = when (appLanguage) { AppLanguage.ES -> AppLanguage.EN; AppLanguage.EN -> AppLanguage.PT; AppLanguage.PT -> AppLanguage.ES }
                            appLanguageCode = next.code
                            preferences.edit().putString("app_language", next.code).apply()
                        }) { Text(appLanguage.code) }
                        IconButton(onClick = { dark = !dark }) { Text(if (dark) "☀" else "◐", style = MaterialTheme.typography.titleLarge) }
                        IconButton(onClick = { showAccountMenu = true }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
                        DropdownMenu(expanded = showAccountMenu, onDismissRequest = { showAccountMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(tr("Mi cuenta", "My account", "Minha conta")) },
                                onClick = { showAccountMenu = false; showAccountDetails = true },
                            )
                            DropdownMenuItem(
                                text = { Text(tr("Política de privacidad", "Privacy policy", "Política de privacidade")) },
                                onClick = { showAccountMenu = false; context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.privacyPolicyUrl))) },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(tr("Cerrar sesión", "Sign out", "Sair")) },
                                onClick = { showAccountMenu = false; firebaseAuth.signOut() },
                            )
                        }
                    }
                )
            },
            bottomBar = { NavigationBar { mainScreens.forEachIndexed { index, screen -> NavigationBarItem(selected = !portfolioOpen && pager.currentPage == index, onClick = { goTo(screen) }, icon = { Text(screen.icon, style = MaterialTheme.typography.titleMedium) }, label = { Text(screenTitle(screen)) }) } } }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                val comparedMarket = storeComparison?.markets?.firstOrNull { it.country == selectedComparisonCountry }
                    ?: storeComparison?.markets?.firstOrNull()
                val sectionReviews = if (storeComparison != null) comparedMarket?.reviews.orEmpty() else reviews
                val sectionInsights = if (storeComparison != null) comparedMarket?.insights?.takeIf { it.size > 0 }?.let { sample ->
                    val negatives = sectionReviews.filter { it.rating in 1..2 }
                    sample.copy(categories = sample.categories.map { (topic, _) -> topic to negatives.count { reviewMatchesTopic(it.text, topic) } }
                        .filter { it.second >= 2 }.sortedByDescending { it.second })
                } else insight
                val sectionMarket = playMarkets.firstOrNull { it.country == comparedMarket?.country } ?: activeMarket
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { index ->
                    when (mainScreens[index]) {
                        Screen.SEARCH -> Search(api, selected, userPlan, creditsRemaining, creditsLimit, { result -> storeComparison = result; if (result != null) { planRefreshTick++; selectedComparisonCountry = result.markets.firstOrNull()?.country; goTo(Screen.DASH) } }, { saveFavorite(it.title); storeComparison = null; selectedComparisonCountry = null; selected = it; job = null; insight = null; reviews = emptyList() }, { storeComparison = null; selectedComparisonCountry = null; selected = null; job = null; insight = null; reviews = emptyList() }, { result, selectedMarket, limit, languages ->
                            activeMarket = selectedMarket; activeReviewLimit = limit; activeReviewLanguages = languages
                            job = result.job; insight = result.insights; reviews = result.reviews; refreshError = false; cacheError = false
                            result.job.creditsRemaining?.let { creditsRemaining = it }
                            val uid = firebaseUser?.uid
                            val analyzedApp = selected
                            if (uid != null && analyzedApp != null) {
                                val saved = SavedAnalysis(analyzedApp, result.insights, result.reviews, selectedMarket.country, limit, languages, System.currentTimeMillis())
                                runCatching { AnalysisCache.save(context, uid, saved) }
                                    .onSuccess { savedAnalyses = it }
                                    .onFailure { cacheError = true }
                            }
                            goTo(Screen.DASH)
                        }, favorites, ::saveFavorite)
                        Screen.DASH -> Dashboard(api, selected, sectionMarket, if (storeComparison != null) null else job, sectionInsights, sectionReviews, refreshingTrend, userPlan == "premium", refreshError, cacheError, savedAnalyses, storeComparison, comparedMarket?.country, { selectedComparisonCountry = it }, ::openSaved, { goTo(Screen.REVIEWS) }, { portfolioOpen = true }) { refreshTick++ }
                        Screen.REVIEWS -> ReviewList(api, selected, sectionInsights, sectionReviews, userPlan == "premium", storeComparison, comparedMarket?.country, { selectedComparisonCountry = it })
                        Screen.ISSUES -> Issues(sectionInsights, sectionReviews, storeComparison, comparedMarket?.country, { selectedComparisonCountry = it })
                        Screen.INSIGHTS -> InsightView(sectionInsights, sectionReviews, storeComparison, comparedMarket?.country, { selectedComparisonCountry = it })
                        Screen.PORTFOLIO -> Unit
                    }
                }
                if (portfolioOpen) Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PortfolioScreen(api, userPlan == "premium", { goTo(Screen.DASH) }) { app ->
                        storeComparison = null; selectedComparisonCountry = null
                        selected = PlayApp(app.id, app.title, app.developer, null, null)
                        job = null; insight = null; reviews = emptyList(); goTo(Screen.SEARCH)
                    }
                }
            }
        }
        if (showAccountDetails) {
            AccountManagementDialog(
                email = firebaseUser?.email.orEmpty(),
                plan = userPlan,
                creditsRemaining = creditsRemaining,
                creditsLimit = creditsLimit,
                onDismiss = { showAccountDetails = false },
                onRefreshPlan = { planRefreshTick++ },
                onDeleteAccount = {
                    showAccountDetails = false
                    accountDeletionError = null
                    showDeleteAccountDialog = true
                },
            )
        }
        if (showDeleteAccountDialog && firebaseUser != null) {
            val user = firebaseUser!!
            val usesPassword = user.providerData.any { it.providerId == "password" }
            val usesGoogle = user.providerData.any { it.providerId == "google.com" }
            val deletionFailedMessage = tr("No se pudo eliminar la cuenta. Intentá nuevamente.", "The account could not be deleted. Try again.", "Não foi possível excluir a conta. Tente novamente.")
            AccountDeletionDialog(
                busy = deletingAccount,
                error = accountDeletionError,
                requiresPassword = usesPassword,
                usesGoogle = usesGoogle,
                onDismiss = { if (!deletingAccount) showDeleteAccountDialog = false },
                onConfirm = { password ->
                    deletingAccount = true
                    accountDeletionError = null
                    navigationScope.launch {
                        runCatching {
                            when {
                                usesPassword -> {
                                    val email = user.email ?: throw IllegalStateException("No encontramos el correo de la cuenta.")
                                    if (password.isBlank()) throw IllegalArgumentException("Ingresá tu contraseña para confirmar la eliminación.")
                                    user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
                                }
                                usesGoogle -> {
                                    val option = GetGoogleIdOption.Builder()
                                        .setFilterByAuthorizedAccounts(false)
                                        .setServerClientId(googleClientId)
                                        .setAutoSelectEnabled(true)
                                        .build()
                                    val credential = credentialManager.getCredential(context, GetCredentialRequest.Builder().addCredentialOption(option).build()).credential
                                    if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) throw IllegalStateException("No se pudo confirmar la cuenta de Google.")
                                    val google = GoogleIdTokenCredential.createFrom(credential.data)
                                    user.reauthenticate(GoogleAuthProvider.getCredential(google.idToken, null)).await()
                                }
                                else -> throw IllegalStateException("No se pudo confirmar el método de acceso de esta cuenta.")
                            }
                            api.deleteAccount()
                            AnalysisCache.clear(context, user.uid)
                            user.delete().await()
                        }.onSuccess {
                            showDeleteAccountDialog = false
                            firebaseAuth.signOut()
                        }.onFailure { error ->
                            accountDeletionError = error.message ?: deletionFailedMessage
                        }
                        deletingAccount = false
                    }
                },
            )
        }
    } }
}

@Composable
private fun AccountManagementDialog(
    email: String,
    plan: String,
    creditsRemaining: Int,
    creditsLimit: Int,
    onDismiss: () -> Unit,
    onRefreshPlan: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Mi cuenta", "My account", "Minha conta")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(email, style = MaterialTheme.typography.bodyMedium)
                Text("${tr("Plan", "Plan", "Plano")}: ${plan.replaceFirstChar { it.uppercase() }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${tr("Créditos de hoy", "Credits today", "Créditos de hoje")}: $creditsRemaining / $creditsLimit", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                TextButton(onClick = onRefreshPlan) { Text(tr("Actualizar plan", "Refresh plan", "Atualizar plano")) }
                HorizontalDivider()
                TextButton(onClick = onDeleteAccount) { Text(tr("Eliminar mi cuenta", "Delete my account", "Excluir minha conta"), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Cerrar", "Close", "Fechar")) } },
    )
}

@Composable
private fun AccountDeletionDialog(
    busy: Boolean,
    error: String?,
    requiresPassword: Boolean,
    usesGoogle: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Eliminar cuenta", "Delete account", "Excluir conta")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Se eliminarán tu cuenta, historial, análisis guardados y datos asociados. Esta acción no se puede deshacer.", "Your account, history, saved analyses, and associated data will be deleted. This action cannot be undone.", "Sua conta, histórico, análises salvas e dados associados serão excluídos. Esta ação não pode ser desfeita."))
                if (requiresPassword) {
                    Text(tr("Confirmá con tu contraseña para continuar.", "Confirm with your password to continue.", "Confirme com sua senha para continuar."))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(tr("Contraseña", "Password", "Senha")) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { TextButton(onClick = { passwordVisible = !passwordVisible }) { Text(if (passwordVisible) tr("Ocultar", "Hide", "Ocultar") else tr("Ver", "Show", "Ver")) } },
                    )
                } else if (usesGoogle) {
                    Text(tr("Te pediremos que elijas tu cuenta de Google para confirmar.", "You'll be asked to choose your Google account to confirm.", "Vamos pedir que você escolha sua conta Google para confirmar."))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = !busy && (!requiresPassword || password.isNotBlank())) {
                Text(if (busy) tr("Eliminando…", "Deleting…", "Excluindo…") else tr("Eliminar definitivamente", "Delete permanently", "Excluir permanentemente"), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(tr("Cancelar", "Cancel", "Cancelar")) } },
    )
}

@Composable private fun IntroScreen(onContinue: () -> Unit) {
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(2200)
        ready = true
    }
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandMark()
            Spacer(Modifier.height(22.dp))
            Text("PlayReview", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            Text(
                tr("Entendé lo que tus usuarios necesitan.", "Understand what your users need.", "Entenda o que seus usuários precisam."),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            IntroFeature("✦", tr("Detectá temas y problemas en tus reseñas.", "Spot themes and issues in your reviews.", "Encontre temas e problemas nas suas avaliações."))
            IntroFeature("◈", tr("Convertí opiniones en decisiones claras.", "Turn feedback into clear decisions.", "Transforme opiniões em decisões claras."))
            IntroFeature("★", tr("Compará mercados y seguí las tendencias.", "Compare markets and follow trends.", "Compare mercados e acompanhe tendências."))
            Spacer(Modifier.height(34.dp))
            Button(onClick = onContinue, enabled = ready, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Continuar al inicio de sesión", "Continue to sign in", "Continuar para entrar"))
            }
            TextButton(onClick = onContinue) {
                Text(tr("Omitir introducción", "Skip introduction", "Pular introdução"))
            }
        }
    }
}

@Composable private fun IntroFeature(icon: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(icon, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable private fun AuthScreen(auth: FirebaseAuth, onUser: (FirebaseUser) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var registering by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val credentialManager = remember { CredentialManager.create(context) }
    val googleClientId = stringResource(com.appinsight.ai.R.string.default_web_client_id)
    val noGoogleAccount = tr("No encontramos una cuenta de Google disponible.", "No Google account is available.", "Não encontramos uma conta Google disponível.")
    Surface(Modifier.fillMaxSize()) { Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(60.dp)); BrandMark()
        Text("PlayReview", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text(if (registering) tr("Creá tu cuenta para guardar análisis e historial.", "Create your account to save analyses and history.", "Crie sua conta para salvar análises e histórico.") else tr("Iniciá sesión para ver tus análisis.", "Sign in to see your analyses.", "Entre para ver suas análises."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.privacyPolicyUrl))) }) {
            Text(tr("Política de privacidad", "Privacy policy", "Política de privacidade"))
        }
        OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true)
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(tr("Contraseña", "Password", "Senha")) },
            singleLine = true,
            visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { TextButton(onClick = { passwordVisible = !passwordVisible }) { Text(if (passwordVisible) tr("Ocultar", "Hide", "Ocultar") else tr("Ver", "Show", "Ver")) } },
        )
        error?.let { ErrorCard(it) }
        OutlinedButton(onClick = {
            busy = true; error = null
            scope.launch {
                runCatching {
                    val option = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(googleClientId)
                        .setAutoSelectEnabled(true)
                        .build()
                    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                    val credential = try {
                        credentialManager.getCredential(context, request).credential
                    } catch (_: NoCredentialException) {
                        throw IllegalStateException(noGoogleAccount)
                    }
                    if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) throw IllegalStateException("No se pudo obtener la cuenta de Google.")
                    val google = GoogleIdTokenCredential.createFrom(credential.data)
                    auth.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)).await().user
                        ?: throw IllegalStateException("Google no devolvió un usuario.")
                }.onSuccess(onUser).onFailure { error = it.message ?: "No se pudo continuar con Google" }
                busy = false
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (registering) tr("Registrarme con Google", "Sign up with Google", "Cadastrar com Google") else tr("Iniciar sesión con Google", "Sign in with Google", "Entrar com Google")) }
        Button(onClick = {
            busy = true; error = null
            scope.launch {
                runCatching {
                    val result = if (registering) auth.createUserWithEmailAndPassword(email.trim(), password).await() else auth.signInWithEmailAndPassword(email.trim(), password).await()
                    val user = result.user ?: throw IllegalStateException("Firebase no devolvió un usuario.")
                    // Send the verification message both after registration and when an
                    // existing, unverified account signs in for the first time.
                    if (registering || !user.isEmailVerified) user.sendEmailVerification().await()
                    user
                }.onSuccess(onUser).onFailure { error = it.message ?: "No se pudo continuar" }
                busy = false
            }
        }, enabled = !busy && email.isNotBlank() && password.length >= 8, modifier = Modifier.fillMaxWidth()) { Text(if (busy) tr("Procesando…", "Processing…", "Processando…") else if (registering) tr("Crear cuenta", "Create account", "Criar conta") else tr("Iniciar sesión", "Sign in", "Entrar")) }
        TextButton(onClick = { registering = !registering; error = null }) { Text(if (registering) tr("Ya tengo una cuenta", "I already have an account", "Já tenho uma conta") else tr("Crear una cuenta", "Create an account", "Criar uma conta")) }
    } }
}

@Composable private fun EmailVerificationScreen(auth: FirebaseAuth, email: String, onVerified: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var checkTick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as ComponentActivity
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) checkTick++
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(activity) {
        while (true) {
            delay(8_000)
            if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) checkTick++
        }
    }
    LaunchedEffect(auth, checkTick) {
        runCatching {
            auth.currentUser?.reload()?.await()
            auth.currentUser?.isEmailVerified == true
        }.onSuccess { if (it) onVerified() }
    }
    Surface(Modifier.fillMaxSize()) { Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(60.dp)); BrandMark()
        Text(tr("Confirmá tu correo", "Confirm your email", "Confirme seu e-mail"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text(tr("Enviamos un enlace de confirmación a $email. Al confirmarlo, volvé a la app: entrarás automáticamente.", "We sent a confirmation link to $email. After confirming it, return to the app and you will enter automatically.", "Enviamos um link de confirmação para $email. Depois de confirmar, volte ao app: você entrará automaticamente."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let { ErrorCard(it) }
        message?.let { InfoCard(it) }
        Button(onClick = {
            busy = true; error = null
            scope.launch {
                runCatching { auth.currentUser?.reload()?.await(); auth.currentUser?.isEmailVerified == true }.onSuccess { verified ->
                    if (verified) onVerified() else error = "Todavía no vemos la confirmación. Abrí el enlace del correo e intentá de nuevo."
                }.onFailure { error = it.message ?: "No se pudo actualizar la cuenta" }
                busy = false
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) tr("Comprobando…", "Checking…", "Verificando…") else tr("Ya confirmé mi correo", "I confirmed my email", "Já confirmei meu e-mail")) }
        OutlinedButton(onClick = {
            busy = true; error = null
            scope.launch {
                runCatching {
                    val user = auth.currentUser ?: throw IllegalStateException("La sesión expiró. Volvé a iniciar sesión.")
                    user.sendEmailVerification().await()
                }.onSuccess { message = "Te enviamos otro enlace de confirmación." }.onFailure { error = it.message ?: "No se pudo reenviar el correo" }
                busy = false
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(tr("Reenviar confirmación", "Resend confirmation", "Reenviar confirmação")) }
        TextButton(onClick = { auth.signOut() }) { Text(tr("Usar otro correo", "Use another email", "Usar outro e-mail")) }
    } }
}

@Composable private fun BrandMark() = Box(
    Modifier.padding(start = 16.dp, end = 10.dp).size(36.dp).clip(RoundedCornerShape(12.dp))
        .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))),
    contentAlignment = Alignment.Center
) { Text("✦", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold) }

@Composable private fun Search(api: ApiClient, selected: PlayApp?, userPlan: String, creditsRemaining: Int, creditsLimit: Int, setComparison: (ComparisonResult?) -> Unit, select: (PlayApp) -> Unit, clearSelection: () -> Unit, setAnalysis: suspend (AnalysisResult, PlayMarket, Int, List<String>?) -> Unit, favorites: List<String>, saveFavorite: (String) -> Unit) {
    var term by rememberSaveable { mutableStateOf("") }; var periodDays by rememberSaveable { mutableIntStateOf(14) }
    var marketCountry by rememberSaveable { mutableStateOf("ar") }
    var maxReviews by rememberSaveable { mutableIntStateOf(50) }
    var languageSelection by rememberSaveable { mutableStateOf("es") }
    var resultsCollapsed by rememberSaveable { mutableStateOf(false) }
    var planExpanded by rememberSaveable { mutableStateOf(false) }
    var compareMode by rememberSaveable { mutableStateOf(false) }
    var compareCountries by rememberSaveable { mutableStateOf(listOf("ar", "mx")) }
    val market = playMarkets.firstOrNull { it.country == marketCountry } ?: playMarkets.first()
    val quotaNotice = tr("No te quedan créditos hoy. Volvé mañana.", "You have no credits left today. Try again tomorrow.", "Você não tem mais créditos hoje. Tente novamente amanhã.")
    var results by remember { mutableStateOf<List<PlayApp>>(emptyList()) }; var error by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var searchTick by remember { mutableIntStateOf(0) }; var startTick by remember { mutableIntStateOf(0) }; var compareTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(userPlan) {
        if (userPlan != "premium" && marketCountry != "ar") {
            marketCountry = "ar"
            languageSelection = "es"
            results = emptyList()
            clearSelection()
        }
        if (userPlan != "premium") compareMode = false
    }
    LaunchedEffect(searchTick) { if (searchTick > 0) {
        val query = term.trim()
        if (query.isBlank()) return@LaunchedEffect
        saveFavorite(query); busy = true; error = null; setComparison(null)
        runCatching { api.search(query, market.country, market.language) }
            .onSuccess { results = it }
            .onFailure { error = it.message ?: "No se pudo buscar" }
        term = ""
        busy = false
    } }
    LaunchedEffect(startTick) { if (startTick > 0 && selected != null) { busy = true; error = null; setComparison(null); val limit = if (userPlan == "premium") maxReviews else 50; val languages = if (userPlan == "premium") languageSelection.split(',').filter { it.isNotBlank() } else null; runCatching { api.analyze(selected, periodDays, market.country, market.language, limit, languages) }.onSuccess { setAnalysis(it, market, limit, languages) }.onFailure {
        val message = it.message ?: "No se pudo analizar"
        if (message.contains("créditos")) {
            scope.launch { snackbarHostState.showSnackbar(quotaNotice, duration = SnackbarDuration.Short) }
        } else error = message
    }; busy = false } }
    LaunchedEffect(compareTick) { if (compareTick > 0 && selected != null && compareCountries.size in 2..3) {
        busy = true; error = null
        runCatching { api.compare(selected, compareCountries) }
            .onSuccess { setComparison(it); resultsCollapsed = true; planExpanded = false }
            .onFailure {
                val message = it.message ?: "No se pudo comparar"
                if (message.contains("créditos")) scope.launch { snackbarHostState.showSnackbar(quotaNotice, duration = SnackbarDuration.Short) }
                else error = message
            }
        busy = false
    } }
    Box(Modifier.fillMaxSize()) {
    Scroll {
        if (userPlan != "premium") AdMobBanner()
        WelcomeHero { example -> term = example; searchTick++ }
        if (favorites.isNotEmpty()) FavoriteSearches(favorites) { favorite -> term = favorite; searchTick++ }
        Text(tr("Buscar la app en", "Find the app in", "Buscar o aplicativo em"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 16.dp)) {
            val premium = userPlan == "premium"
            playMarkets.forEach { option -> item {
                val locked = !premium && option.country != "ar"
                FilterChip(
                    selected = market.country == option.country,
                    enabled = !locked,
                    onClick = { marketCountry = option.country; languageSelection = option.language.takeIf { it in listOf("es", "en", "pt") } ?: "es"; results = emptyList(); setComparison(null); compareMode = false; clearSelection() },
                    label = { Text("${option.flag} ${option.label}") },
                    leadingIcon = if (locked) ({ Text("🔒", style = MaterialTheme.typography.labelSmall) }) else null
                )
            } }
        }
        Text(tr("Esta tienda sirve para encontrar la app. Después podés analizarla aquí o comparar sus datos y reseñas con otros países.", "This store is used to find the app. Then you can analyze it here or compare its data and reviews across countries.", "Esta loja serve para encontrar o aplicativo. Depois você pode analisá-lo aqui ou comparar seus dados e avaliações com outros países."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(term, { term = it }, Modifier.fillMaxWidth(), label = { Text(tr("¿Qué aplicación querés analizar?", "Which app do you want to analyze?", "Qual aplicativo você quer analisar?")) }, placeholder = { Text(tr("Ej.: WhatsApp, Mercado Libre…", "E.g.: WhatsApp, Spotify…", "Ex.: WhatsApp, Spotify…")) }, singleLine = true, shape = RoundedCornerShape(18.dp))
        Button(onClick = { resultsCollapsed = false; planExpanded = selected != null; searchTick++ }, enabled = term.trim().length > 1 && !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text(if (busy) tr("Buscando…", "Searching…", "Buscando…") else tr("Buscar en Google Play", "Search Google Play", "Buscar no Google Play")) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth()); error?.let { ErrorCard(it) }
        AnimatedVisibility(selected != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            selected?.let { app ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { planExpanded = !planExpanded }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Text("${tr("Plan de análisis", "Analysis plan", "Plano de análise")}  ${if (planExpanded) "⌃" else "⌄"}", fontWeight = FontWeight.SemiBold)
                    }
                    AnimatedVisibility(planExpanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        AnalysisOptions(app, periodDays, { periodDays = it }, userPlan, creditsRemaining, creditsLimit, maxReviews, { maxReviews = it }, languageSelection.split(',').filter { it.isNotBlank() }, { selectedLanguages -> languageSelection = selectedLanguages.joinToString(",") }, compareMode, { enabled ->
                            compareMode = enabled
                            if (enabled) {
                                val other = compareCountries.firstOrNull { it != marketCountry }
                                    ?: playMarkets.firstOrNull { it.country != marketCountry }?.country
                                compareCountries = listOfNotNull(marketCountry, other)
                            }
                        }, compareCountries, { compareCountries = it }, marketCountry)
                    }
                }
            }
        }
        if (results.isNotEmpty() && resultsCollapsed) {
            TextButton(onClick = { resultsCollapsed = false }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Mostrar resultados de búsqueda (${results.size})", "Show search results (${results.size})", "Mostrar resultados da busca (${results.size})"))
            }
        }
        if (!resultsCollapsed) results.forEach { app ->
            AppResult(app, app.id == selected?.id) { select(app); setComparison(null); compareMode = false; resultsCollapsed = true; planExpanded = true }
        }
        if (selected != null) Spacer(Modifier.height(72.dp))
    }
    SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp).padding(bottom = if (selected != null) 76.dp else 8.dp)) { data ->
        Snackbar(snackbarData = data, shape = RoundedCornerShape(16.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface)
    }
    selected?.let {
        val effectiveLimit = if (userPlan == "premium") maxReviews else 50
        val analyzeLabel = when {
            busy && compareMode -> tr("Comparando países…", "Comparing countries…", "Comparando países…")
            busy -> tr("Analizando reseñas…", "Analyzing reviews…", "Analisando avaliações…")
            compareMode && compareCountries.size in 2..3 -> tr("Comparar ${compareCountries.size} países", "Compare ${compareCountries.size} countries", "Comparar ${compareCountries.size} países")
            compareMode -> tr("Elegí otro país", "Choose another country", "Escolha outro país")
            else -> tr("Analizar hasta $effectiveLimit reseñas · $periodDays días", "Analyze up to $effectiveLimit reviews · $periodDays days", "Analisar até $effectiveLimit avaliações · $periodDays dias")
        }
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = MaterialTheme.colorScheme.background.copy(alpha = .96f), shadowElevation = 8.dp) {
            Button(onClick = { if (compareMode) compareTick++ else startTick++ }, enabled = !busy && (!compareMode || compareCountries.size in 2..3), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(52.dp), shape = RoundedCornerShape(16.dp)) {
                Text(analyzeLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
    }
}

@Composable private fun WelcomeHero(onExample: (String) -> Unit) {
    val motion = rememberInfiniteTransition(label = "welcomeMotion")
    val glow by motion.animateFloat(0.78f, 1.08f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "glow")
    val lift by motion.animateFloat(0f, -9f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "lift")
    Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFF312E81), Color(0xFF2563EB), Color(0xFF06B6D4)))).padding(22.dp)) {
        Box(Modifier.align(Alignment.TopEnd).offset { androidx.compose.ui.unit.IntOffset(15.dp.roundToPx(), (8.dp + lift.dp).roundToPx()) }.size(104.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f * glow)))
        Box(Modifier.align(Alignment.BottomEnd).offset { androidx.compose.ui.unit.IntOffset((-10).dp.roundToPx(), (14.dp - lift.dp).roundToPx()) }.size(62.dp).clip(CircleShape).background(Color(0xFFFFD166).copy(alpha = .72f * glow)))
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) { Text("●", color = Color(0xFF86EFAC)); Text(tr("ANÁLISIS EN VIVO", "LIVE ANALYSIS", "ANÁLISE AO VIVO"), color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(tr("Entendé a tus usuarios\nantes de que se vayan.", "Understand your users\nbefore they leave.", "Entenda seus usuários\nantes que eles saiam."), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold); Text(tr("Detectá oportunidades, problemas y tendencias en reseñas de Google Play.", "Find opportunities, issues and trends in Google Play reviews.", "Encontre oportunidades, problemas e tendências nas avaliações do Google Play."), color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.bodyMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { HeroStat("50", tr("reseñas", "reviews", "avaliações")); HeroStat("3", tr("por día", "per day", "por dia")); HeroStat("5★", tr("gráfico", "chart", "gráfico")) }
        }
    }
    Text(tr("Probá con una búsqueda", "Try a search", "Experimente uma busca"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(tr("Deslizá para descubrir más aplicaciones", "Swipe to discover more apps", "Deslize para descobrir mais aplicativos"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val suggestions = listOf("WhatsApp", "Instagram", "Mercado Libre", "Spotify", "TikTok", "Uber", "YouTube", "Netflix", "Google Maps", "PedidosYa", "Rappi", "Banco Nación")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 16.dp)) {
        items(suggestions.size) { index ->
            val suggestion = suggestions[index]
            QuickSearch(suggestion) { onExample(suggestion) }
        }
    }
}

@Composable private fun HeroStat(value: String, label: String) = Column(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .16f)).padding(horizontal = 10.dp, vertical = 6.dp)) { Text(value, color = Color.White, fontWeight = FontWeight.ExtraBold); Text(label, color = Color.White.copy(alpha = .82f), style = MaterialTheme.typography.labelSmall) }
@Composable private fun QuickSearch(label: String, onClick: () -> Unit) = AssistChip(onClick = onClick, label = { Text(label, fontWeight = FontWeight.SemiBold) }, leadingIcon = { Text("⌕") })
@Composable private fun FavoriteSearches(favorites: List<String>, onClick: (String) -> Unit) {
    Text(tr("Favoritas", "Favorites", "Favoritas"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 16.dp)) {
        items(favorites.size) { index ->
            val favorite = favorites[index]
            AssistChip(onClick = { onClick(favorite) }, label = { Text(favorite, fontWeight = FontWeight.SemiBold) }, leadingIcon = { Text("★", color = Color(0xFFFFB703)) })
        }
    }
}

@Composable private fun AnalysisOptions(app: PlayApp, periodDays: Int, setPeriod: (Int) -> Unit, userPlan: String, creditsRemaining: Int, creditsLimit: Int, maxReviews: Int, setMaxReviews: (Int) -> Unit, languages: List<String>, setLanguages: (List<String>) -> Unit, compareMode: Boolean, setCompareMode: (Boolean) -> Unit, compareCountries: List<String>, setCompareCountries: (List<String>) -> Unit, currentCountry: String) {
    val premium = userPlan == "premium"
    val effectiveLimit = if (premium) maxReviews else 50
    ElevatedCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(tr("Plan de análisis", "Analysis plan", "Plano de análise"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(if (premium) tr("Tu plan: Premium", "Your plan: Premium", "Seu plano: Premium") else tr("Tu plan: Gratuito", "Your plan: Free", "Seu plano: Gratuito"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("${tr("Créditos disponibles hoy", "Credits available today", "Créditos disponíveis hoje")}: $creditsRemaining / $creditsLimit", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(app.title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(tr("¿Qué querés hacer con esta app?", "What do you want to do with this app?", "O que você quer fazer com este aplicativo?"), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FilterChip(selected = !compareMode, onClick = { setCompareMode(false) }, label = { Text(tr("Reseñas", "Reviews", "Avaliações")) })
                FilterChip(selected = compareMode, enabled = premium, onClick = { setCompareMode(true) }, label = { Text(tr("Comparar países", "Compare countries", "Comparar países")) }, leadingIcon = if (!premium) ({ Text("🔒", style = MaterialTheme.typography.labelSmall) }) else null)
            }
            if (compareMode && premium) {
                Text(tr("La app se encontró en ${playMarkets.firstOrNull { it.country == currentCountry }?.label ?: currentCountry.uppercase()}. Elegí 1 o 2 países más para comparar la misma app.", "The app was found in ${playMarkets.firstOrNull { it.country == currentCountry }?.label ?: currentCountry.uppercase()}. Choose 1 or 2 more countries to compare the same app.", "O aplicativo foi encontrado em ${playMarkets.firstOrNull { it.country == currentCountry }?.label ?: currentCountry.uppercase()}. Escolha mais 1 ou 2 países para comparar o mesmo aplicativo."), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .78f))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(end = 12.dp)) {
                    playMarkets.filter { it.country != currentCountry }.forEach { option -> item {
                        val chosen = option.country in compareCountries
                        FilterChip(
                            selected = chosen,
                            enabled = chosen || compareCountries.size < 3,
                            onClick = {
                                setCompareCountries(if (chosen) compareCountries.filterNot { it == option.country } else compareCountries + option.country)
                            },
                            label = { Text("${option.flag} ${option.label}") }
                        )
                    } }
                }
            }
            if (compareMode) {
                Text(tr("Cada país muestra su calificación pública y una muestra de hasta 50 reseñas de los últimos 30 días, con temas y acciones propios. La comparación usa una consulta Premium.", "Each country shows its public rating and a sample of up to 50 reviews from the last 30 days, with its own topics and actions. The comparison uses one Premium query.", "Cada país mostra sua nota pública e uma amostra de até 50 avaliações dos últimos 30 dias, com temas e ações próprios. A comparação usa uma consulta Premium."), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .78f))
            } else {
                Text(tr("¿De qué período?", "Which period?", "Qual período?"), fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    ChoiceChip("7 días", periodDays == 7) { setPeriod(7) }; ChoiceChip("14 días", periodDays == 14) { setPeriod(14) }; ChoiceChip("1 mes", periodDays == 30) { setPeriod(30) }
                }
                Text(tr("Cantidad de reseñas", "Number of reviews", "Quantidade de avaliações"), fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(50, 100, 500).forEach { limit ->
                        FilterChip(selected = effectiveLimit == limit, onClick = { setMaxReviews(limit) }, enabled = premium || limit == 50, label = { Text(if (limit == 50) "50" else "$limit ★") })
                    }
                }
                Text(if (premium) tr("Idiomas de consulta · elegí uno o varios", "Review search languages · select one or more", "Idiomas de consulta · selecione um ou mais") else tr("Plan gratuito: 3 créditos diarios; una consulta de 50 reseñas cuesta 1 crédito. Premium: 30 créditos diarios.", "Free: 3 credits a day; a 50-review analysis costs 1 credit. Premium: 30 credits a day.", "Grátis: 3 créditos por dia; uma análise de 50 avaliações custa 1 crédito. Premium: 30 créditos por dia."), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .78f))
                if (premium) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("es" to "Español", "en" to "English", "pt" to "Português").forEach { (code, name) ->
                            FilterChip(selected = code in languages, onClick = { val next = if (code in languages) languages.filterNot { it == code } else languages + code; if (next.isNotEmpty()) setLanguages(next) }, label = { Text(name) })
                        }
                    }
                    Text(tr("Google Play puede incluir reseñas de otros idiomas. Las etiquetas del listado se calculan a partir del texto.", "Google Play may include reviews in other languages. List labels are estimated from the text.", "O Google Play pode incluir avaliações em outros idiomas. As etiquetas da lista são estimadas pelo texto."), style = MaterialTheme.typography.labelSmall)
                }
                Text(tr("La muestra crece con el período. Si hay menos reseñas disponibles, se analizan todas las encontradas.", "The sample grows with the period. If fewer reviews are available, all found reviews are analyzed.", "A amostra cresce com o período. Se houver menos avaliações disponíveis, todas as encontradas serão analisadas."), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .75f))
            }
        }
    }
}

@Composable private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.05f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "chipScale")
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
}

@Composable private fun ComparisonCountryPicker(comparison: ComparisonResult, selectedCountry: String?, selectCountry: (String) -> Unit) {
    Text(tr("País de la comparación", "Comparison country", "País da comparação"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        comparison.markets.forEach { result -> item {
            val market = playMarkets.firstOrNull { it.country == result.country }
            FilterChip(selected = result.country == selectedCountry, onClick = { selectCountry(result.country) }, label = { Text("${market?.flag ?: "🌐"} ${market?.label ?: result.country.uppercase()}") })
        } }
    }
    val current = comparison.markets.firstOrNull { it.country == selectedCountry }
    if (current?.reviewError != null) ErrorCard(current.reviewError)
    else if (current != null && current.reviews.isEmpty()) Text(tr("No se obtuvieron reseñas para esta tienda en los últimos 30 días.", "No reviews were retrieved for this store in the last 30 days.", "Não foram obtidas avaliações para esta loja nos últimos 30 dias."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun ComparisonWeakPoint(comparison: ComparisonResult, selectedCountry: String?) {
    val result = comparison.markets.firstOrNull { it.country == selectedCountry } ?: return
    val country = playMarkets.firstOrNull { it.country == result.country }
    val countryName = "${country?.flag ?: "🌐"} ${country?.label ?: result.country.uppercase()}"
    val negatives = result.reviews.filter { it.rating in 1..2 }
    val weakPoint = result.insights?.categories?.map { (topic, _) -> topic to negatives.count { reviewMatchesTopic(it.text, topic) } }
        ?.filter { it.second >= 2 }?.maxByOrNull { it.second }
    if (weakPoint != null) Text(tr("Punto débil en la muestra de $countryName: ${topicLabel(weakPoint.first)} (${weakPoint.second} reseñas negativas).", "Weak point in the $countryName sample: ${topicLabel(weakPoint.first)} (${weakPoint.second} negative reviews).", "Ponto fraco na amostra de $countryName: ${topicLabel(weakPoint.first)} (${weakPoint.second} avaliações negativas)."), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
}

@Composable private fun Dashboard(api: ApiClient, app: PlayApp?, market: PlayMarket, job: ScrapeJob?, data: Insights?, reviews: List<RealReview>, refreshingTrend: Boolean, premium: Boolean, refreshError: Boolean, cacheError: Boolean, savedAnalyses: List<SavedAnalysis>, comparison: ComparisonResult?, selectedCountry: String?, selectCountry: (String) -> Unit, openSaved: (SavedAnalysis) -> Unit, openReport: () -> Unit, openPortfolio: () -> Unit, refreshTrend: () -> Unit) = Scroll {
    if (!premium) AdMobBanner()
    Text(app?.title ?: tr("Tu panel de análisis", "Your analysis dashboard", "Seu painel de análise"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
    if (comparison != null) {
        ComparisonCountryPicker(comparison, selectedCountry, selectCountry)
        CountryComparisonView(comparison, selectedCountry)
        ComparisonWeakPoint(comparison, selectedCountry)
    }
    if (refreshError) ErrorCard(tr("No se pudo actualizar. Se mantienen los datos que ya tenías cargados.", "Could not refresh. Your previously loaded data remains available.", "Não foi possível atualizar. Os dados já carregados continuam disponíveis."))
    if (cacheError) ErrorCard(tr("No se pudo guardar este análisis para usarlo sin internet.", "Could not save this analysis for offline use.", "Não foi possível salvar esta análise para uso sem internet."))
    if (premium) OutlinedButton(onClick = openReport, modifier = Modifier.fillMaxWidth()) {
        Text(tr("Informe PDF Premium → Reseñas", "Premium PDF report → Reviews", "Relatório PDF Premium → Avaliações"))
    }
    if (premium && app != null && data != null) SummaryPdfButton(api, app, market, data, reviews)
    if (comparison == null) Button(onClick = openPortfolio, modifier = Modifier.fillMaxWidth()) {
        Text(if (premium) tr("Mis apps analizadas · Premium", "My analyzed apps · Premium", "Meus apps analisados · Premium") else tr("Mis últimos 3 análisis", "My latest 3 analyses", "Minhas 3 análises mais recentes"))
    }
    if (data == null) { if (comparison != null) InfoCard(tr("Elegí otro país para ver el análisis de su muestra de reseñas.", "Choose another country to view its review sample analysis.", "Escolha outro país para ver a análise da amostra de avaliações.")) else if (job == null) HeroCard(tr("Todavía no hay datos", "No data yet", "Ainda não há dados"), tr("Elegí una aplicación desde Buscar para crear tu primer informe.", "Choose an app in Search to create your first report.", "Escolha um aplicativo em Buscar para criar seu primeiro relatório.")) else if (job.status in listOf("failed", "cancelled")) ErrorCard(job.error ?: tr("El análisis no pudo completarse.", "The analysis could not be completed.", "A análise não pôde ser concluída.")) else AnalysisWait() }
    else {
        val source = if (comparison != null) "${market.flag} ${market.label}" else null
        Text(if (source == null) tr("Panorama de las reseñas recientes", "Recent reviews overview", "Visão geral das avaliações recentes") else tr("Muestra de reseñas: $source", "Review sample: $source", "Amostra de avaliações: $source"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        if (source != null) Text(tr("Los tres gráficos siguientes corresponden solamente a $source.", "The next three charts belong only to $source.", "Os próximos três gráficos correspondem somente a $source."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Metric("%.1f".format(data.average), tr("calificación media", "average rating", "avaliação média"), Modifier.weight(1f), Color(0xFFFFB703)); Metric("${data.size}", tr("reseñas analizadas", "reviews analyzed", "avaliações analisadas"), Modifier.weight(1f), MaterialTheme.colorScheme.primary) }
        RatingTrendCard(data, refreshingTrend, refreshTrend, source); RatingChart(reviews, source); SentimentCard(data, source)
    }
    if (comparison == null && savedAnalyses.isNotEmpty()) {
        HorizontalDivider(Modifier.padding(top = 12.dp))
        Text(tr("Historial de análisis", "Analysis history", "Histórico de análises"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(tr("Tus análisis anteriores, disponibles sin conexión.", "Your previous analyses, available offline.", "Suas análises anteriores, disponíveis offline."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        savedAnalyses.take(if (premium) 12 else 3).forEach { saved ->
            ElevatedCard(shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(saved.app.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${tr("Tienda", "Store", "Loja")} ${saved.marketCountry.uppercase()} · ${saved.reviews.size} ${tr("reseñas", "reviews", "avaliações")} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(saved.savedAt))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { openSaved(saved) }) { Text(tr("Abrir", "Open", "Abrir")) }
                }
            }
        }
    }
}

@Composable private fun AdMobBanner() {
    AndroidView(
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = AppConfig.testBannerAdUnit
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                loadAd(AdRequest.Builder().build())
            }
        },
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
    )
}

@Composable private fun SummaryPdfButton(api: ApiClient, app: PlayApp, market: PlayMarket, data: Insights, reviews: List<RealReview>) {
    var exporting by remember { mutableStateOf(false) }
    var pdfBytes by remember { mutableStateOf<ByteArray?>(null) }
    var exportError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val localeCode = LocalAppLanguage.current.code
    val fileError = tr("No se pudo guardar el PDF del resumen.", "Could not save the overview PDF.", "Não foi possível salvar o PDF do resumo.")
    val premiumRequired = tr("Esta descarga requiere Premium activo.", "This download requires an active Premium plan.", "Este download requer Premium ativo.")
    val createPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val content = pdfBytes
        pdfBytes = null
        if (uri != null && content != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri, "w") ?: error(fileError)
                    output.use { it.write(content) }
                }
            }.onFailure { exportError = it.message ?: fileError }
        }
    }
    Button(onClick = {
        exporting = true
        exportError = null
        scope.launch {
            runCatching {
                if (api.profile().name != "premium") throw IllegalStateException(premiumRequired)
                withContext(Dispatchers.Default) { SummaryPdfReport.create(app.title, market.label, data, reviews, localeCode) }
            }.onSuccess { bytes ->
                pdfBytes = bytes
                createPdf.launch("PlayReview-Resumen-${app.id.replace('.', '-').take(45)}.pdf")
            }.onFailure { exportError = it.message ?: fileError }
            exporting = false
        }
    }, enabled = !exporting, modifier = Modifier.fillMaxWidth()) {
        Text(if (exporting) tr("Preparando resumen…", "Preparing overview…", "Preparando resumo…") else tr("Descargar resumen Premium en PDF", "Download Premium overview PDF", "Baixar resumo Premium em PDF"))
    }
    exportError?.let { ErrorCard(it) }
}

@Composable private fun PortfolioScreen(api: ApiClient, premium: Boolean, onBack: () -> Unit, onAnalyze: (PortfolioApp) -> Unit) {
    var refreshKey by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var apps by remember { mutableStateOf<List<PortfolioApp>>(emptyList()) }
    LaunchedEffect(refreshKey) {
        loading = true; error = null
        runCatching { api.portfolio() }
            .onSuccess { apps = it }
            .onFailure { error = it.message ?: "No se pudo cargar el historial" }
        loading = false
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!premium) AdMobBanner()
                TextButton(onClick = onBack) { Text(tr("← Volver al resumen", "← Back to overview", "← Voltar ao resumo")) }
                Text(tr("Mis apps analizadas", "My analyzed apps", "Meus apps analisados"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text(tr("Compará tus análisis anteriores y detectá qué apps necesitan atención.", "Compare previous analyses and see which apps need attention.", "Compare análises anteriores e veja quais apps precisam de atenção."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { refreshKey++ }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                    Text(if (loading) tr("Cargando…", "Loading…", "Carregando…") else tr("Actualizar historial", "Refresh history", "Atualizar histórico"))
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { ErrorCard(it) }
                if (!loading && error == null && apps.isEmpty()) InfoCard(tr("Tus apps aparecerán aquí después del primer análisis. Buscá una app para comenzar.", "Your apps will appear here after your first analysis. Search for an app to begin.", "Seus apps aparecerão aqui após a primeira análise. Busque um app para começar."))
                if (apps.isNotEmpty()) Text(tr("${apps.size} apps en tu historial reciente", "${apps.size} apps in your recent history", "${apps.size} apps no seu histórico recente"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
        items(apps.size) { index -> PortfolioAppCard(apps[index], premium, onAnalyze) }
        if (apps.isNotEmpty()) item {
            Text(tr("Cada comparación muestra dos muestras de reseñas obtenidas en fechas distintas. El historial visible depende de tu plan.", "Each comparison uses two review samples collected on different dates. Visible history depends on your plan.", "Cada comparação usa duas amostras de avaliações obtidas em datas diferentes. O histórico visível depende do seu plano."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun PortfolioAppCard(app: PortfolioApp, premium: Boolean, onAnalyze: (PortfolioApp) -> Unit) {
    val latest = app.runs.firstOrNull() ?: return
    val previous = app.runs.getOrNull(1)
    val change = previous?.let { latest.average - it.average }
    val negativeShare = if (latest.reviewCount > 0) latest.negativeCount * 100 / latest.reviewCount else 0
    var expanded by rememberSaveable(app.id) { mutableStateOf(!premium) }
    ElevatedCard(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(app.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            app.developer?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(tr("${app.runs.size} análisis recientes disponibles", "${app.runs.size} recent analyses available", "${app.runs.size} análises recentes disponíveis"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(tr("Último análisis: ${latest.date.take(10)} · ${latest.reviewCount} reseñas · ${latest.periodDays} días", "Latest analysis: ${latest.date.take(10)} · ${latest.reviewCount} reviews · ${latest.periodDays} days", "Última análise: ${latest.date.take(10)} · ${latest.reviewCount} avaliações · ${latest.periodDays} dias"), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric("%.2f ★".format(latest.average), tr("promedio", "average", "média"), Modifier.weight(1f), MaterialTheme.colorScheme.primary)
                Metric("$negativeShare%", tr("negativas", "negative", "negativas"), Modifier.weight(1f), Color(0xFFEF4444))
            }
            if (change != null) {
                val changeColor = if (change < -0.3) Color(0xFFEF4444) else if (change > 0.3) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant
                Text(tr("Cambio frente al análisis anterior: %+.2f ★".format(change), "Change from previous analysis: %+.2f ★".format(change), "Mudança em relação à análise anterior: %+.2f ★".format(change)), color = changeColor, fontWeight = FontWeight.Bold)
                if (change < -0.3) Text(tr("Señal para revisar: bajó el promedio de la muestra.", "Worth checking: the sample average fell.", "Vale revisar: a média da amostra caiu."), color = changeColor, style = MaterialTheme.typography.bodySmall)
            } else Text(tr("Analizá esta app otra vez para comparar resultados.", "Analyze this app again to compare results.", "Analise este app novamente para comparar resultados."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) { Text(if (expanded) tr("Ocultar historial", "Hide history", "Ocultar histórico") else tr("Ver historial", "View history", "Ver histórico")) }
            Button(onClick = { onAnalyze(app) }, modifier = Modifier.fillMaxWidth()) { Text(tr("Analizar otra vez", "Analyze again", "Analisar novamente")) }
            if (expanded) app.runs.forEach { run ->
                val negatives = if (run.reviewCount > 0) run.negativeCount * 100 / run.reviewCount else 0
                HorizontalDivider()
                Text("${run.date.take(10)}  ·  ${"%.2f".format(run.average)} ★  ·  $negatives% ${tr("negativas", "negative", "negativas")}", fontWeight = FontWeight.SemiBold)
                Text(tr("${run.reviewCount} reseñas · ${run.periodDays} días", "${run.reviewCount} reviews · ${run.periodDays} days", "${run.reviewCount} avaliações · ${run.periodDays} dias"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun RatingTrendCard(data: Insights, refreshing: Boolean, refresh: () -> Unit, source: String? = null) {
    var selectedDays by rememberSaveable { mutableIntStateOf(7) }
    val trend = when (selectedDays) { 7 -> data.weekTrend; 14 -> data.twoWeekTrend; else -> data.monthTrend }
    if (trend == null) {
        ElevatedCard(shape = RoundedCornerShape(22.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tr("Tendencia de calificaciones", "Rating trend", "Tendência de avaliações"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                source?.let { Text(tr("Datos de $it", "Data from $it", "Dados de $it"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                Text(tr("Este análisis se realizó antes de incorporar la tendencia. Volvé a analizar la app para ver la evolución de 7, 14 y 30 días.", "This analysis was made before trend tracking was added. Analyze the app again to see the 7, 14 and 30-day evolution.", "Esta análise foi feita antes da tendência ser adicionada. Analise o aplicativo novamente para ver a evolução de 7, 14 e 30 dias."), color = MaterialTheme.colorScheme.onSecondaryContainer)
                Button(onClick = refresh, enabled = !refreshing, modifier = Modifier.fillMaxWidth()) { Text(if (refreshing) tr("Actualizando tendencia…", "Updating trend…", "Atualizando tendência…") else tr("Actualizar tendencia", "Update trend", "Atualizar tendência")) }
            }
        }
        return
    }
    val statusColor = when (trend.direction) { "positive" -> Color(0xFF22C55E); "negative" -> Color(0xFFEF4444); else -> Color(0xFFF59E0B) }
    val graphColor = Color(0xFF1D9BF0)
    val trendReviewCount = trend.points.sumOf { it.count }
    val label = when (trend.direction) { "positive" -> tr("Las calificaciones subieron", "Ratings went up", "As avaliações subiram"); "negative" -> tr("Las calificaciones bajaron", "Ratings went down", "As avaliações caíram"); else -> tr("Las calificaciones se mantuvieron", "Ratings stayed the same", "As avaliações se mantiveram") }
    ElevatedCard(shape = RoundedCornerShape(22.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text(tr("Tendencia de calificaciones", "Rating trend", "Tendência de avaliações"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(if (source == null) tr("Promedio diario de las reseñas", "Daily review average", "Média diária das avaliações") else tr("Datos de $source · promedio diario", "Data from $source · daily average", "Dados de $source · média diária"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(tr("$trendReviewCount reseñas en esta ventana", "$trendReviewCount reviews in this window", "$trendReviewCount avaliações nesta janela"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                Text("%.1f ★".format(trend.average), color = graphColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ChoiceChip(tr("7 días", "7 days", "7 dias"), selectedDays == 7) { selectedDays = 7 }; ChoiceChip(tr("14 días", "14 days", "14 dias"), selectedDays == 14) { selectedDays = 14 }; ChoiceChip(tr("1 mes", "1 month", "1 mês"), selectedDays == 30) { selectedDays = 30 } }
            if (trend.points.size < 2) {
                InfoCard(tr("Todavía no hay suficientes días con reseñas para dibujar la tendencia.", "There are not enough days with reviews to draw the trend yet.", "Ainda não há dias suficientes com avaliações para mostrar a tendência."))
            } else {
                val scores = trend.points.map { it.average.toFloat() }
                val observedMin = scores.minOrNull() ?: 1f
                val observedMax = scores.maxOrNull() ?: 5f
                val center = (observedMin + observedMax) / 2f
                val halfRange = maxOf((observedMax - observedMin) / 2f + .12f, .20f)
                val chartMin = (center - halfRange).coerceAtLeast(1f)
                val chartMax = (center + halfRange).coerceAtMost(5f)
                val chartSurface = MaterialTheme.colorScheme.surfaceVariant
                Text(tr("Escala ampliada: ${"%.1f".format(chartMin)} a ${"%.1f".format(chartMax)} estrellas", "Expanded scale: ${"%.1f".format(chartMin)} to ${"%.1f".format(chartMax)} stars", "Escala ampliada: ${"%.1f".format(chartMin)} a ${"%.1f".format(chartMax)} estrelas"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Canvas(Modifier.fillMaxWidth().height(160.dp)) {
                    val step = size.width / (trend.points.size - 1)
                    fun point(index: Int) = Offset(index * step, size.height - ((trend.points[index].average.toFloat() - chartMin) / (chartMax - chartMin)) * size.height)
                    repeat(3) { line ->
                        val y = size.height * line / 2f
                        drawLine(color = Color.White.copy(alpha = .08f), start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 2f)
                    }
                    val points = trend.points.indices.map(::point)
                    fun smoothPath(): Path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (index in 1 until points.size) {
                            val previous = points[index - 1]
                            val current = points[index]
                            val middleX = (previous.x + current.x) / 2f
                            cubicTo(middleX, previous.y, middleX, current.y, current.x, current.y)
                        }
                    }
                    val area = Path().apply {
                        moveTo(points.first().x, size.height)
                        lineTo(points.first().x, points.first().y)
                        for (index in 1 until points.size) {
                            val previous = points[index - 1]
                            val current = points[index]
                            val middleX = (previous.x + current.x) / 2f
                            cubicTo(middleX, previous.y, middleX, current.y, current.x, current.y)
                        }
                        lineTo(points.last().x, size.height)
                        close()
                    }
                    drawPath(area, brush = Brush.verticalGradient(listOf(graphColor.copy(alpha = .34f), graphColor.copy(alpha = .02f)), endY = size.height))
                    drawPath(smoothPath(), color = graphColor, style = Stroke(width = 6f, cap = StrokeCap.Round))
                    val latest = points.last()
                    drawCircle(color = chartSurface, radius = 11f, center = latest)
                    drawCircle(color = graphColor, radius = 7f, center = latest)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(trend.points.first().day.takeLast(5), style = MaterialTheme.typography.labelSmall); Text("${tr("Hoy", "Today", "Hoje")} · ${"%.2f".format(trend.points.last().average)} ★", color = graphColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
            }
            Row(Modifier.clip(RoundedCornerShape(12.dp)).background(statusColor.copy(alpha = .12f)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (trend.direction == "positive") "↑" else if (trend.direction == "negative") "↓" else "→", color = statusColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                val detail = tr("De ${"%.2f".format(trend.firstScore ?: 0.0)}★ al inicio a ${"%.2f".format(trend.latestScore ?: 0.0)}★ al final", "From ${"%.2f".format(trend.firstScore ?: 0.0)}★ at the start to ${"%.2f".format(trend.latestScore ?: 0.0)}★ at the end", "De ${"%.2f".format(trend.firstScore ?: 0.0)}★ no início a ${"%.2f".format(trend.latestScore ?: 0.0)}★ no final")
                Column { Text(label, color = statusColor, fontWeight = FontWeight.Bold); Text(detail, color = statusColor, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable private fun RatingChart(reviews: List<RealReview>, source: String? = null) {
    val counts = (5 downTo 1).associateWith { star -> reviews.count { it.rating == star } }; val max = (counts.values.maxOrNull() ?: 1).coerceAtLeast(1)
    ElevatedCard(shape = RoundedCornerShape(22.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(tr("Distribución de calificaciones", "Rating distribution", "Distribuição de avaliações"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(if (source == null) tr("Cantidad de reseñas por estrellas", "Number of reviews by stars", "Quantidade de avaliações por estrelas") else tr("Muestra de reseñas de $source", "Review sample from $source", "Amostra de avaliações de $source"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); (5 downTo 1).forEach { star -> val count = counts[star] ?: 0; val color = when (star) { 5 -> Color(0xFF22C55E); 4 -> Color(0xFF84CC16); 3 -> Color(0xFFF59E0B); 2 -> Color(0xFFFB923C); else -> Color(0xFFEF4444) }; Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) { Text("$star ★", modifier = Modifier.width(34.dp), fontWeight = FontWeight.Bold, color = color); LinearProgressIndicator(progress = { count.toFloat() / max }, modifier = Modifier.weight(1f).height(10.dp), color = color, trackColor = color.copy(alpha = .16f)); Text("$count", modifier = Modifier.width(34.dp), fontWeight = FontWeight.Bold) } } } }
}

@Composable private fun SentimentCard(data: Insights, source: String? = null) { val positive = data.sentiments["positive"] ?: 0; val neutral = data.sentiments["neutral"] ?: 0; val negative = data.sentiments["negative"] ?: 0; ElevatedCard(shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(tr("Clima de las opiniones", "Review sentiment", "Sentimento das avaliações"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); source?.let { Text(tr("Muestra de $it", "Sample from $it", "Amostra de $it"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Sentiment(tr("Positivas", "Positive", "Positivas"), positive, Color(0xFF22C55E)); Sentiment(tr("Neutrales", "Neutral", "Neutras"), neutral, Color(0xFFF59E0B)); Sentiment(tr("Negativas", "Negative", "Negativas"), negative, Color(0xFFEF4444)) } } } }
@Composable private fun Sentiment(label: String, value: Int, color: Color) = Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("$value", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = color); Text(label, style = MaterialTheme.typography.labelSmall) }
@Composable private fun AppResult(app: PlayApp, chosen: Boolean, onSelect: () -> Unit) = ElevatedCard(shape = RoundedCornerShape(18.dp), colors = CardDefaults.elevatedCardColors(containerColor = if (chosen) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(app.title, fontWeight = FontWeight.Bold); Text(app.developer ?: tr("Desarrollador no disponible", "Developer unavailable", "Desenvolvedor indisponível"), style = MaterialTheme.typography.bodySmall); app.score?.let { Text("★ %.1f".format(it), color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold) } }; OutlinedButton(onClick = onSelect) { Text(if (chosen) tr("Elegida", "Selected", "Selecionado") else tr("Elegir", "Select", "Selecionar")) } } }
@Composable private fun HeroCard(title: String, text: String) = Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) { Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold); Text(text, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .86f)) } }
@Composable private fun ErrorCard(text: String) = Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(16.dp)) { Text(text, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer) }
@Composable private fun Metric(value: String, label: String, modifier: Modifier, color: Color) = ElevatedCard(modifier, shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp)) { Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = color); Text(label, style = MaterialTheme.typography.labelMedium) } }
@Composable private fun AnalysisWait() { val tips = listOf(tr("Leyendo reseñas recientes.", "Reading recent reviews.", "Lendo avaliações recentes."), tr("Agrupando comentarios por tema.", "Grouping comments by topic.", "Agrupando comentários por tema."), tr("Calculando el tono de cada opinión.", "Calculating the tone of each review.", "Calculando o tom de cada avaliação.")); var tip by remember { mutableIntStateOf(0) }; LaunchedEffect(Unit) { while (true) { delay(2500); tip = (tip + 1) % tips.size } }; HeroCard(tr("Analizando reseñas…", "Analyzing reviews…", "Analisando avaliações…"), tips[tip]); LinearProgressIndicator(Modifier.fillMaxWidth()) }
@Composable private fun ReviewList(api: ApiClient, app: PlayApp?, data: Insights?, items: List<RealReview>, premium: Boolean, comparison: ComparisonResult?, selectedCountry: String?, selectCountry: (String) -> Unit) {
    var selectedOption by rememberSaveable { mutableStateOf("all") }
    var selectedLanguage by rememberSaveable { mutableStateOf("all") }
    val premiumOnlyOptions = setOf("detailed", "star1", "star5", "recent")
    LaunchedEffect(premium) {
        if (!premium && selectedOption in premiumOnlyOptions) selectedOption = "all"
    }
    var exporting by remember { mutableStateOf(false) }
    var pdfBytes by remember { mutableStateOf<ByteArray?>(null) }
    var exportError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val localeCode = LocalAppLanguage.current.code
    val premiumRequired = tr("Esta descarga requiere Premium activo.", "This download requires an active Premium plan.", "Este download requer Premium ativo.")
    val analysisRequired = tr("Primero analizá una app para crear el informe.", "Analyze an app first to create the report.", "Analise um aplicativo primeiro para criar o relatório.")
    val fileError = tr("No se pudo guardar el PDF.", "Could not save the PDF.", "Não foi possível salvar o PDF.")
    val reportTitle = tr("Informe de reseñas", "Reviews report", "Relatório de avaliações")
    val createPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val content = pdfBytes
        pdfBytes = null
        if (uri != null && content != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri, "w") ?: error(fileError)
                    output.use { it.write(content) }
                }
            }.onFailure { exportError = it.message ?: fileError }
        }
    }
    val filteredByType = when (selectedOption) {
        "recent" -> items.sortedByDescending { it.date ?: "" }
        "best" -> items.sortedByDescending { it.rating }
        "worst" -> items.sortedBy { it.rating }
        "detailed" -> items.filter { it.text.length >= 80 }.sortedByDescending { it.text.length }
        "star1" -> items.filter { it.rating == 1 }
        "star2" -> items.filter { it.rating == 2 }
        "star3" -> items.filter { it.rating == 3 }
        "star4" -> items.filter { it.rating == 4 }
        "star5" -> items.filter { it.rating == 5 }
        "negative" -> items.filter { it.rating <= 2 }
        "positive" -> items.filter { it.rating >= 4 }
        else -> items
    }
    val visible = if (premium && selectedLanguage != "all") filteredByType.filter { it.language == selectedLanguage } else filteredByType
    val viewLabel = reviewOptionLabel(selectedOption)
    val languageLabel = when (selectedLanguage) {
        "es" -> "Español"
        "en" -> "English"
        "pt" -> "Português"
        "und" -> tr("Otros/sin identificar", "Other/unknown", "Outros/não identificados")
        else -> tr("Todos", "All", "Todos")
    }
    val filterDescription = tr("Vista: $viewLabel · Idioma: $languageLabel", "View: $viewLabel · Language: $languageLabel", "Visão: $viewLabel · Idioma: $languageLabel")

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      item { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(tr("Reseñas recientes", "Recent reviews", "Avaliações recentes"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
    Text(app?.title ?: tr("Elegí una app", "Select an app", "Selecione um aplicativo"))
    if (comparison != null) ComparisonCountryPicker(comparison, selectedCountry, selectCountry)
    Text(tr("Informe PDF", "PDF report", "Relatório PDF"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Button(onClick = {
        exporting = true; exportError = null
        scope.launch {
            runCatching {
                if (data == null || visible.isEmpty()) throw IllegalStateException(analysisRequired)
                if (api.profile().name != "premium") throw IllegalStateException(premiumRequired)
                withContext(Dispatchers.Default) { PdfReport.create(app?.title ?: reportTitle, visible, items.size, filterDescription, localeCode) }
            }.onSuccess { bytes ->
                pdfBytes = bytes
                val fileName = "PlayReview-${app?.id?.replace('.', '-')?.take(45) ?: "reviews"}.pdf"
                createPdf.launch(fileName)
            }.onFailure { exportError = it.message ?: fileError }
            exporting = false
        }
    }, enabled = premium && visible.isNotEmpty() && data != null && !exporting, modifier = Modifier.fillMaxWidth()) {
        Text(if (exporting) tr("Preparando PDF…", "Preparing PDF…", "Preparando PDF…") else tr("Crear informe Premium en PDF", "Create Premium PDF report", "Criar relatório Premium em PDF"))
    }
    when {
        !premium -> InfoCard(tr("Premium permite crear un informe PDF con gráficos, hallazgos y las reseñas que elijas con los filtros.", "Premium lets you create a PDF report with charts, findings and the reviews you select with filters.", "Premium permite criar um relatório PDF com gráficos, descobertas e as avaliações selecionadas com filtros."))
        items.isEmpty() || data == null -> InfoCard(analysisRequired)
        visible.isEmpty() -> Text(tr("No hay reseñas para exportar con estos filtros.", "No reviews to export with these filters.", "Não há avaliações para exportar com estes filtros."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Text(tr("El informe incluirá solo las ${visible.size} reseñas visibles, en este orden. Las cifras y los gráficos se calcularán con esas reseñas.", "The report will include only the ${visible.size} visible reviews, in this order. Figures and charts will use those reviews.", "O relatório incluirá apenas as ${visible.size} avaliações visíveis, nesta ordem. Os números e gráficos usarão essas avaliações."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    exportError?.let { ErrorCard(it) }
    if (items.isEmpty()) {
        HeroCard(tr("Sin reseñas todavía", "No reviews yet", "Ainda não há avaliações"), tr("Cuando termines un análisis, las opiniones aparecerán aquí.", "After you finish an analysis, reviews will appear here.", "Quando terminar uma análise, as avaliações aparecerão aqui."))
    } else {
        if (premium) {
            Text(tr("Idioma de las reseñas", "Review language", "Idioma das avaliações"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val languageFilters = listOf("all" to tr("Todos", "All", "Todos"), "es" to "Español", "en" to "English", "pt" to "Português", "und" to tr("Otros/sin identificar", "Other/unknown", "Outros/não identificados"))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 16.dp)) {
                languageFilters.forEach { (code, label) ->
                    item { FilterChip(selected = selectedLanguage == code, onClick = { selectedLanguage = code }, label = { Text(label) }) }
                }
            }
        }
        Text(tr("Elegí una vista", "Choose a view", "Escolha uma visualização"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 16.dp)) {
            listOf("all", "detailed", "negative", "positive", "star1", "star5").forEach { option ->
                item {
                    val locked = !premium && option in premiumOnlyOptions
                    FilterChip(selected = selectedOption == option, enabled = !locked, onClick = { selectedOption = option }, label = { Text(reviewOptionLabel(option)) }, leadingIcon = if (locked) ({ Text("🔒", style = MaterialTheme.typography.labelSmall) }) else null)
                }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 16.dp)) {
            listOf("star2", "star3", "star4").forEach { option ->
                item { FilterChip(selected = selectedOption == option, onClick = { selectedOption = option }, label = { Text(reviewOptionLabel(option)) }) }
            }
        }
        Text(tr("También podés ordenar", "You can also sort", "Você também pode ordenar"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 16.dp)) {
            listOf("recent", "best", "worst").forEach { option ->
                item {
                    val locked = !premium && option in premiumOnlyOptions
                    FilterChip(selected = selectedOption == option, enabled = !locked, onClick = { selectedOption = option }, label = { Text(reviewOptionLabel(option)) }, leadingIcon = { Text(if (locked) "🔒" else if (option == "recent") "◷" else "★") })
                }
            }
        }
        Text(tr("Mostrando ${visible.size} de ${items.size} reseñas", "Showing ${visible.size} of ${items.size} reviews", "Mostrando ${visible.size} de ${items.size} avaliações"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (visible.isEmpty()) {
            InfoCard(tr("No hay reseñas que coincidan con este filtro. Probá con otra opción.", "No reviews match this filter. Try another option.", "Nenhuma avaliação corresponde a este filtro. Tente outra opção."))
        }
    }
      } }
      items(visible.size) { index ->
        val review = visible[index]
        ElevatedCard(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${"★".repeat(review.rating)}${"☆".repeat(5 - review.rating)}", color = Color(0xFFF59E0B))
                Text(review.text, maxLines = 6, overflow = TextOverflow.Ellipsis)
                Text("${tr("Idioma", "Language", "Idioma")}: ${if (review.language == "und") "?" else review.language.uppercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
      }
    }
}
@Composable private fun CountryComparisonView(comparison: ComparisonResult, selectedCountry: String?) {
    val result = comparison.markets.firstOrNull { it.country == selectedCountry } ?: comparison.markets.firstOrNull() ?: return
    val market = playMarkets.firstOrNull { it.country == result.country }
    val others = comparison.markets.filter { it.country != result.country }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(tr("Calificaciones por tienda", "Ratings by store", "Avaliações por loja"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        ElevatedCard(shape = RoundedCornerShape(20.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${market?.flag ?: "🌐"} ${market?.label ?: result.country.uppercase()} · ${result.language.uppercase()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (!result.available || result.score == null) {
                    Text(result.error ?: tr("No se obtuvo una calificación en esta tienda.", "No rating was retrieved from this store.", "Não foi obtida uma avaliação nesta loja."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Metric("${result.scoreText ?: "%.1f".format(result.score)} ★", tr("calificación pública", "public rating", "avaliação pública"), Modifier.weight(1f), Color(0xFFFFB703))
                        Metric(formatCount(result.ratings), tr("calificaciones", "ratings", "avaliações"), Modifier.weight(1f), MaterialTheme.colorScheme.primary)
                    }
                    PublicRatingDistribution(result.histogram)
                }
            }
        }
        if (others.isNotEmpty()) {
            Text(tr("Otras tiendas comparadas", "Other compared stores", "Outras lojas comparadas"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            others.forEach { other ->
                val otherMarket = playMarkets.firstOrNull { it.country == other.country }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${otherMarket?.flag ?: "🌐"} ${otherMarket?.label ?: other.country.uppercase()}")
                    Text(if (other.score == null) "—" else "${other.scoreText ?: "%.1f".format(other.score)} ★", fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(tr("Calificaciones públicas de Google Play; la muestra de reseñas se muestra debajo.", "Public Google Play ratings; the review sample appears below.", "Avaliações públicas do Google Play; a amostra de avaliações aparece abaixo."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatCount(value: Long): String = when {
    value >= 1_000_000_000 -> "%.1fB".format(value / 1_000_000_000.0)
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
    value >= 1_000 -> "%.1fK".format(value / 1_000.0)
    else -> value.toString()
}

@Composable private fun PublicRatingDistribution(histogram: Map<Int, Long>) {
    val maximum = (histogram.values.maxOrNull() ?: 1L).coerceAtLeast(1L)
    Text(tr("Distribución de estrellas", "Star distribution", "Distribuição de estrelas"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    (5 downTo 1).forEach { star ->
        val count = histogram[star] ?: 0L
        val color = when (star) { 5 -> Color(0xFF22C55E); 4 -> Color(0xFF84CC16); 3 -> Color(0xFFF59E0B); 2 -> Color(0xFFFB923C); else -> Color(0xFFEF4444) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$star ★", modifier = Modifier.width(28.dp), style = MaterialTheme.typography.labelSmall, color = color)
            LinearProgressIndicator(progress = { count.toFloat() / maximum }, modifier = Modifier.weight(1f).height(7.dp), color = color, trackColor = color.copy(alpha = .16f))
            Text(formatCount(count), modifier = Modifier.width(48.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable private fun Issues(data: Insights?, reviews: List<RealReview>, comparison: ComparisonResult?, selectedCountry: String?, selectCountry: (String) -> Unit) = Scroll {
    Text(tr("Temas detectados", "Detected topics", "Temas detectados"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
    Text(tr("Patrones que se repiten en los comentarios públicos de la app activa.", "Patterns repeated in public comments for the active app.", "Padrões repetidos nos comentários públicos do aplicativo ativo."))
    if (comparison != null) ComparisonCountryPicker(comparison, selectedCountry, selectCountry)
    if (data == null) {
        HeroCard(tr("Aún no hay temas", "No topics yet", "Ainda não há temas"), tr("Iniciá un análisis para descubrirlos.", "Start an analysis to discover them.", "Inicie uma análise para descobri-los."))
    } else if (data.categories.isEmpty()) {
        HeroCard(tr("Sin temas repetidos", "No recurring topics", "Sem temas recorrentes"), tr("Analizamos ${data.size} reseñas, pero no hubo suficientes coincidencias para formar un patrón.", "We analyzed ${data.size} reviews, but there were not enough matches to form a pattern.", "Analisamos ${data.size} avaliações, mas não houve correspondências suficientes para formar um padrão."))
    } else {
        val mainTopic = data.categories.first()
        val coverage = if (data.size == 0) 0 else mainTopic.second * 100 / data.size
        HeroCard(tr("${data.categories.size} temas encontrados", "${data.categories.size} topics found", "${data.categories.size} temas encontrados"), tr("El más mencionado es ${topicLabel(mainTopic.first)}: aparece en $coverage% de las reseñas analizadas.", "The most mentioned is ${topicLabel(mainTopic.first)}: it appears in $coverage% of analyzed reviews.", "O mais mencionado é ${topicLabel(mainTopic.first)}: aparece em $coverage% das avaliações analisadas."))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("${data.size}", tr("reseñas leídas", "reviews read", "avaliações lidas"), Modifier.weight(1f), MaterialTheme.colorScheme.primary)
            Metric("${data.categories.sumOf { it.second }}", tr("menciones", "mentions", "menções"), Modifier.weight(1f), Color(0xFF8B5CF6))
        }
        Text(tr("Frecuencia e impacto", "Frequency and impact", "Frequência e impacto"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        data.categories.forEachIndexed { index, (topic, count) -> ThemeInsightCard(index + 1, topic, count, data.size) }
        Text(tr("Comentarios que explican los problemas", "Reviews describing the issues", "Comentários que explicam os problemas"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(tr("Reseñas negativas relacionadas con los temas detectados.", "Negative reviews related to the detected topics.", "Avaliações negativas relacionadas aos temas detectados."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val examples = selectProblemExamples(data.categories, reviews)
        if (examples.isEmpty()) {
            InfoCard(tr("No encontramos reseñas negativas que expliquen estos temas con suficiente detalle. Podés ver todas las opiniones en Reseñas.", "We did not find negative reviews that explain these topics in enough detail. You can see all opinions in Reviews.", "Não encontramos avaliações negativas que expliquem esses temas com detalhes suficientes. Você pode ver todas as opiniões em Avaliações."))
        } else examples.forEach { example -> ReviewExcerpt(example.topic, example.review) }
        InfoCard(tr("Un tema frecuente no siempre es el más urgente: contrastá estas señales con la calificación, el impacto de negocio y tus métricas internas.", "A frequent topic is not always the most urgent: compare these signals with rating, business impact and internal metrics.", "Um tema frequente nem sempre é o mais urgente: compare estes sinais com a avaliação, o impacto no negócio e suas métricas internas."))
    }
}

@Composable private fun ThemeInsightCard(index: Int, topic: String, count: Int, total: Int) {
    val percentage = if (total == 0) 0 else count * 100 / total
    val level = when { percentage >= 25 -> tr("Muy frecuente", "Very frequent", "Muito frequente"); percentage >= 10 -> tr("Frecuente", "Frequent", "Frequente"); else -> tr("En seguimiento", "Monitoring", "Em acompanhamento") }
    val color = when { percentage >= 25 -> Color(0xFFEF4444); percentage >= 10 -> Color(0xFFF59E0B); else -> MaterialTheme.colorScheme.primary }
    ElevatedCard(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#$index", color = color, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.width(12.dp))
                Text(topicLabel(topic), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(level, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(progress = { percentage / 100f }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = color, trackColor = color.copy(alpha = .16f))
            Text(tr("$count menciones en $total reseñas · $percentage% del total", "$count mentions in $total reviews · $percentage% of total", "$count menções em $total avaliações · $percentage% do total"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class ProblemExample(val topic: String, val review: RealReview)

private val problemPhrases = listOf(
    "no puedo", "no me deja", "no funciona", "no abre", "no carga", "no llega", "error", "falla", "problema", "bloquea", "se cierra", "congela", "tarda", "cobró", "no recibo",
    "can't", "cannot", "doesn't work", "won't", "failed", "fails", "broken", "stuck", "crash", "issue", "problem", "freezes", "slow", "charged",
    "não consigo", "não funciona", "não abre", "não carrega", "erro", "falha", "problema", "travando", "fecha sozinho", "demora", "não recebo", "cobrança"
)

private fun selectProblemExamples(categories: List<Pair<String, Int>>, reviews: List<RealReview>): List<ProblemExample> {
    val candidates = reviews.filter { review ->
        val text = review.text.trim()
        review.rating in 1..2 && text.length >= 15 && text.count { it.isLetter() } >= 12 &&
            text.split(Regex("\\s+")).size >= 3 &&
            (text.length >= 70 || problemPhrases.any { text.contains(it, ignoreCase = true) })
    }
    fun score(review: RealReview): Int =
        problemPhrases.count { review.text.contains(it, ignoreCase = true) } * 20 +
            minOf(review.text.length, 240) / 12 + if (review.rating == 1) 2 else 0

    val ranked = categories.flatMap { (topic, _) ->
        candidates.filter { reviewMatchesTopic(it.text, topic) }.map { ProblemExample(topic, it) }
    }.sortedByDescending { score(it.review) }
    val chosen = mutableListOf<ProblemExample>()
    val usedTexts = mutableSetOf<String>()
    fun add(example: ProblemExample) {
        if (chosen.size < 3 && usedTexts.add(example.review.text.trim().lowercase())) chosen += example
    }
    categories.forEach { (topic, _) ->
        ranked.firstOrNull { it.topic == topic && it.review.text.trim().lowercase() !in usedTexts }?.let(::add)
    }
    ranked.forEach(::add)
    return chosen
}

@Composable private fun ReviewExcerpt(topic: String, review: RealReview) = ElevatedCard(shape = RoundedCornerShape(18.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(topicLabel(topic), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text("${"★".repeat(review.rating)}${"☆".repeat(5 - review.rating)}", color = Color(0xFFF59E0B))
        Text("“${review.text}”", maxLines = if (expanded) Int.MAX_VALUE else 6, overflow = TextOverflow.Ellipsis)
        if (review.text.length > 180) TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) tr("Ver menos", "Show less", "Ver menos") else tr("Leer comentario completo", "Read full review", "Ler avaliação completa"))
        }
        Text("${tr("Idioma", "Language", "Idioma")}: ${if (review.language == "und") "?" else review.language.uppercase()}${review.date?.let { " · ${it.take(10)}" }.orEmpty()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun InfoCard(text: String) = ElevatedCard(shape = RoundedCornerShape(18.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) { Text(text, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSecondaryContainer) }

internal fun reviewMatchesTopic(text: String, topic: String): Boolean {
    val words = when (topic.lowercase()) {
        "inicio de sesión" -> listOf("login", "iniciar sesión", "contraseña", "cuenta", "sign in", "password", "account", "senha", "entrar", "conta")
        "rendimiento" -> listOf("lenta", "lent", "congela", "lag", "slow", "sluggish", "freezes", "travando", "devagar")
        "cierres inesperados" -> listOf("cierra", "crash", "se sale", "reinicia", "closes", "stops", "keeps stopping", "fecha", "parou")
        "notificaciones" -> listOf("notificación", "notificaciones", "notification", "notifications", "notificação", "notificações")
        "conectividad" -> listOf("conexión", "internet", "conecta", "connection", "connectivity", "offline", "conexão")
        "pagos" -> listOf("pago", "cobro", "tarjeta", "payment", "charge", "card", "pagamento", "cobrança", "cartão")
        else -> listOf(topic)
    }
    return words.any { text.contains(it, ignoreCase = true) }
}
@Composable private fun InsightView(data: Insights?, reviews: List<RealReview>, comparison: ComparisonResult?, selectedCountry: String?, selectCountry: (String) -> Unit) = Scroll {
    Text(tr("Plan de acción", "Action plan", "Plano de ação"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
    Text(tr("Qué mejorar primero a partir de los temas detectados.", "What to improve first based on detected topics.", "O que melhorar primeiro com base nos temas detectados."))
    if (comparison != null) ComparisonCountryPicker(comparison, selectedCountry, selectCountry)
    if (data == null) {
        HeroCard(tr("Esperando datos", "Waiting for data", "Aguardando dados"), tr("Analizá una app para recibir acciones priorizadas.", "Analyze an app to receive prioritized actions.", "Analise um aplicativo para receber ações priorizadas."))
    } else if (data.categories.isEmpty()) {
        HeroCard(tr("Sin prioridades claras", "No clear priorities", "Sem prioridades claras"), tr("No encontramos temas repetidos suficientes para recomendar una acción.", "We did not find enough recurring topics to recommend an action.", "Não encontramos temas recorrentes suficientes para recomendar uma ação."))
    } else {
        val negativeReviews = reviews.filter { it.rating in 1..2 }
        val problems = data.categories.map { (topic, _) -> topic to negativeReviews.count { reviewMatchesTopic(it.text, topic) } }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(3)
        if (problems.isEmpty()) {
            HeroCard(tr("Sin problemas concretos en la muestra", "No specific issues in this sample", "Sem problemas concretos nesta amostra"), tr("Los temas detectados no aparecen en reseñas negativas. Revisá los comentarios antes de definir una solución.", "Detected topics do not appear in negative reviews. Read the comments before choosing a fix.", "Os temas detectados não aparecem em avaliações negativas. Leia os comentários antes de definir uma solução."))
        } else {
            HeroCard(tr("Qué revisar primero", "What to check first", "O que verificar primeiro"), tr("Estas acciones parten de las reseñas negativas. Comprobá la causa en la app antes de aplicar un cambio.", "These actions are based on negative reviews. Confirm the cause in the app before making a change.", "Estas ações partem das avaliações negativas. Confirme a causa no aplicativo antes de fazer mudanças."))
            problems.forEachIndexed { index, (topic, count) ->
                val example = selectProblemExamples(listOf(topic to count), negativeReviews).firstOrNull()?.review
                ActionCard(index + 1, topic, count, negativeReviews.size, example)
            }
        }
    }
}

@Composable private fun ActionCard(priority: Int, topic: String, count: Int, negativeTotal: Int, example: RealReview?) {
    val check = when (topic.lowercase()) {
        "inicio de sesión", "cuenta" -> tr("Reproducí el ingreso y la recuperación de contraseña; identificá el paso y el mensaje de error que se repite.", "Reproduce sign-in and password recovery; identify the step and error message that keep failing.", "Reproduza o login e a recuperação de senha; identifique a etapa e a mensagem de erro recorrentes.")
        "rendimiento", "lentitud" -> tr("Medí cuánto tardan el inicio y la pantalla mencionada, también con red lenta y teléfonos modestos.", "Measure startup and the mentioned screen, including slow networks and lower-end phones.", "Meça o tempo de inicialização e da tela mencionada, também com rede lenta e celulares básicos.")
        "cierres inesperados", "errores", "fallas" -> tr("Agrupá los cierres por versión y modelo de teléfono; buscá el error más frecuente en los registros.", "Group crashes by app version and phone model; find the most frequent error in crash logs.", "Agrupe os fechamentos por versão e modelo do celular; encontre o erro mais frequente nos registros.")
        "notificaciones" -> tr("Comprobá permisos, entrega, duplicados y el destino al tocar la notificación en Android.", "Check permissions, delivery, duplicates and where tapping the notification leads on Android.", "Verifique permissões, entrega, duplicações e o destino ao tocar na notificação no Android.")
        "conectividad" -> tr("Probá el flujo con internet lento y sin conexión; observá si pierde datos o queda cargando sin fin.", "Test the flow with slow and no internet; check whether it loses data or loads forever.", "Teste o fluxo com internet lenta e sem conexão; veja se perde dados ou fica carregando sem fim.")
        "pagos", "suscripción", "precio" -> tr("Compará el cobro registrado con el estado que ve el usuario; revisá pagos duplicados y operaciones pendientes.", "Compare recorded charges with the status shown to the user; check duplicate and pending payments.", "Compare a cobrança registrada com o status visto pelo usuário; verifique pagamentos duplicados e pendentes.")
        else -> tr("Reproducí el caso descrito en la reseña y contrastalo con los registros de la app.", "Reproduce the case described in the review and compare it with app logs.", "Reproduza o caso descrito na avaliação e compare com os registros do aplicativo.")
    }
    val action = when (topic.lowercase()) {
        "inicio de sesión", "cuenta" -> tr("Corregí el paso que falla y mostrale al usuario cómo recuperar el acceso sin perder su cuenta.", "Fix the failing step and give users a clear way to recover access without losing their account.", "Corrija a etapa que falha e mostre como recuperar o acesso sem perder a conta.")
        "rendimiento", "lentitud" -> tr("Reducí la operación más lenta y mostrale al usuario el progreso y una opción de reintento.", "Speed up the slowest operation and show progress with a retry option.", "Acelere a operação mais lenta e mostre o progresso com uma opção de tentar novamente.")
        "cierres inesperados", "errores", "fallas" -> tr("Corregí primero el cierre que afecta a más usuarios y verificá el arreglo en esos dispositivos antes de publicar.", "Fix the crash affecting the most users first and verify it on those devices before releasing.", "Corrija primeiro o fechamento que afeta mais usuários e teste nesses dispositivos antes de publicar.")
        "notificaciones" -> tr("Corregí la entrega o el enlace que falla; permití ajustar las alertas que resulten excesivas.", "Fix failed delivery or links, and let users control alerts they find excessive.", "Corrija a entrega ou os links com falhas e permita ajustar alertas excessivos.")
        "conectividad" -> tr("Mostrá un error claro, conservá lo ingresado y ofrecé reintentar cuando vuelva la conexión.", "Show a clear error, preserve entered data and offer retry when connectivity returns.", "Mostre um erro claro, preserve os dados inseridos e permita tentar novamente quando a conexão voltar.")
        "pagos", "suscripción", "precio" -> tr("Evitá repetir cobros al reintentar, informá el estado real del pago y resolvé los duplicados.", "Prevent repeat charges on retry, show the actual payment status and resolve duplicates.", "Evite cobranças repetidas ao tentar novamente, mostre o status real e resolva duplicações.")
        else -> tr("Corregí el caso reproducido y comprobá si disminuyen las quejas del mismo tema.", "Fix the reproduced case and check whether complaints about this topic decrease.", "Corrija o caso reproduzido e verifique se as reclamações sobre o tema diminuem.")
    }
    ElevatedCard(shape = RoundedCornerShape(20.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$priority", modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(horizontal = 10.dp, vertical = 5.dp), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(topicLabel(topic), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(tr("$count de $negativeTotal reseñas negativas", "$count of $negativeTotal negative reviews", "$count de $negativeTotal avaliações negativas"), style = MaterialTheme.typography.labelMedium)
                }
            }
            if (example != null) {
                Text(tr("Reseña que lo describe", "Review describing it", "Avaliação que descreve o problema"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text("“${example.text}”", maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            Text(tr("Qué revisar", "What to check", "O que verificar"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(check)
            Text(tr("Acción sugerida", "Suggested action", "Ação sugerida"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(action)
        }
    }
}
@Composable private fun Scroll(content: @Composable ColumnScope.() -> Unit) = LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content) } }
