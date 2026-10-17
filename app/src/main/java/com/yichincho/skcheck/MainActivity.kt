package com.yichincho.skcheck

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import com.yichincho.skcheck.data.Side
import com.yichincho.skcheck.data.Store
import com.yichincho.skcheck.ui.AddScreen
import com.yichincho.skcheck.ui.AppTheme
import com.yichincho.skcheck.ui.DetailScreen
import com.yichincho.skcheck.ui.JournalScreen
import com.yichincho.skcheck.ui.NewShotScreen
import com.yichincho.skcheck.ui.SavedShotScreen
import com.yichincho.skcheck.ui.SettingsScreen
import com.yichincho.skcheck.ui.TradeScreen
import com.yichincho.skcheck.ui.WatchScreen
import com.yichincho.skcheck.work.Notifier

class MainActivity : ComponentActivity() {
    /** A screenshot shared into the app, waiting to be reviewed. */
    private val incomingShot = mutableStateOf<Uri?>(null)
    /** A stock to open, from a tapped notification. */
    private val incomingSymbol = mutableStateOf<String?>(null)

    private val askNotify = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        if (savedInstanceState == null) handle(intent)
        if (!Notifier.canNotify(this)) requestNotify()
        setContent {
            AppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppRoot(incomingShot, incomingSymbol, ::requestNotify)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        Store.refreshInBackground()
    }

    private fun requestNotify() {
        if (Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun handle(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { incomingShot.value = it }
        }
        intent.getStringExtra(Notifier.EXTRA_SYMBOL)?.let { incomingSymbol.value = it }
    }
}

/** Screens outside the tab bar keep clear of the status and navigation bars themselves. */
@Composable
private fun FullScreen(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().systemBarsPadding()) { content() }
}

@Composable
private fun AppRoot(incomingShot: MutableState<Uri?>, incomingSymbol: MutableState<String?>, askNotify: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var shotView by rememberSaveable { mutableStateOf<String?>(null) }
    var trade by rememberSaveable { mutableStateOf<String?>(null) }
    var tradeSide by rememberSaveable { mutableStateOf(Side.BUY) }
    var pickedShot by rememberSaveable { mutableStateOf<Uri?>(null) }

    val sym = incomingSymbol.value
    LaunchedEffect(sym) {
        if (sym != null) {
            detail = sym
            shotView = null
            trade = null
            incomingSymbol.value = null
        }
    }
    val sharedUri = incomingShot.value
    LaunchedEffect(sharedUri) {
        if (sharedUri != null) {
            pickedShot = sharedUri
            incomingShot.value = null
        }
    }

    val newShot = pickedShot
    val tradeSym = trade
    val shotId = shotView
    val detailSym = detail
    when {
        newShot != null -> FullScreen {
            BackHandler { pickedShot = null }
            NewShotScreen(newShot, onDone = { pickedShot = null; tab = 2 })
        }
        tradeSym != null -> FullScreen {
            BackHandler { trade = null }
            TradeScreen(tradeSym, tradeSide, onDone = { trade = null })
        }
        shotId != null -> FullScreen {
            BackHandler { shotView = null }
            SavedShotScreen(shotId, onBack = { shotView = null }, onOpenStock = { shotView = null; detail = it })
        }
        detailSym != null -> FullScreen {
            BackHandler { detail = null }
            DetailScreen(
                detailSym,
                onBack = { detail = null },
                onTrade = { side -> tradeSide = side; trade = detailSym },
                onOpenShot = { shotView = it },
            )
        }
        else -> Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                        icon = { Icon(Icons.AutoMirrored.Rounded.TrendingUp, contentDescription = null) }, label = { Text("觀察") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                        icon = { Icon(Icons.Rounded.Add, contentDescription = null) }, label = { Text("新增") })
                    NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                        icon = { Icon(Icons.Rounded.EditNote, contentDescription = null) }, label = { Text("日誌") })
                    NavigationBarItem(selected = tab == 3, onClick = { tab = 3 },
                        icon = { Icon(Icons.Rounded.Settings, contentDescription = null) }, label = { Text("設定") })
                }
            },
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                when (tab) {
                    0 -> WatchScreen(onOpen = { detail = it }, onAdd = { tab = 1 })
                    1 -> AddScreen(onShot = { pickedShot = it })
                    2 -> JournalScreen(onOpenShot = { shotView = it }, onOpenStock = { detail = it })
                    else -> SettingsScreen(askNotify)
                }
            }
        }
    }
}
