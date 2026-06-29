/**
 * MakClean - Gamified Gallery Cleaner
 * Developed by/Author: schwarmak-dev (https://github.com/schwarmak-dev)
 * 
 * Copyright (c) 2026 schwarmak-dev. All rights reserved.
 */
package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppUiState
import com.example.ui.MakCleanViewModel
import com.example.ui.screens.FiltersScreen
import com.example.ui.screens.MainDeckScreen
import com.example.ui.screens.PermissionScreen
import com.example.ui.screens.ScanningScreen
import com.example.ui.screens.SuccessCelebration
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MakCleanViewModel = viewModel()
            val activeTheme by viewModel.activeTheme.collectAsStateWithLifecycle()
            MyApplicationTheme(activeTheme = activeTheme) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        MakCleanApp(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun MakCleanApp(
    viewModel: MakCleanViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    
    val remainingItems by viewModel.remainingItems.collectAsStateWithLifecycle()
    val keptItems by viewModel.keptItems.collectAsStateWithLifecycle()
    val trashedItems by viewModel.trashedItems.collectAsStateWithLifecycle()
    val favoritedItems by viewModel.favoritedItems.collectAsStateWithLifecycle()
    val privacyActive by viewModel.privacyMode.collectAsStateWithLifecycle()

    // Permissions check dispatcher. On Android 14+ we also request the "selected photos"
    // permission so the app works when the user grants partial access.
    val permissionsToRequest = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
        else -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    var isPermissionGranted by remember { mutableStateOf(hasGalleryAccess(context)) }

    LaunchedEffect(isPermissionGranted) {
        viewModel.loadStorageAnalytics(context, isPermissionGranted)
    }

    // Re-check access whenever we come back to the app (e.g. after granting it in Settings),
    // so the permission gate opens automatically.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isPermissionGranted = hasGalleryAccess(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Re-check actual access (full or partial); the gate opens when access is granted.
        isPermissionGranted = hasGalleryAccess(context)
    }

    val deleteIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onDeletionConfirmed(context)
        } else {
            viewModel.onDeletionCancelled(context)
        }
    }

    if (!isPermissionGranted) {
        // No gallery access: show a clear permission screen instead of silent demo content.
        PermissionScreen(
            onRequestPermission = { permissionLauncher.launch(permissionsToRequest) },
            onOpenSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
        )
        return
    }

    Crossfade(
        targetState = uiState,
        label = "AppScreenTransition"
    ) { state ->
        when (state) {
            is AppUiState.FilterSelection -> {
                val analytics by viewModel.storageAnalytics.collectAsStateWithLifecycle()
                val activeTheme by viewModel.activeTheme.collectAsStateWithLifecycle()
                FiltersScreen(
                    selectedFilter = selectedFilter,
                    onFilterSelected = { viewModel.setFilter(it) },
                    analytics = analytics,
                    activeTheme = activeTheme,
                    onThemeSelected = { viewModel.setTheme(it) },
                    onStartScan = {
                        if (hasGalleryAccess(context)) {
                            viewModel.startScan(context, true)
                        } else {
                            // Access was revoked while open — fall back to the permission gate.
                            isPermissionGranted = false
                        }
                    }
                )
            }
            is AppUiState.Scanning -> {
                ScanningScreen()
            }
            is AppUiState.DeckActive -> {
                val currentSortOrder by viewModel.deckSortOrder.collectAsStateWithLifecycle()
                MainDeckScreen(
                    remainingItems = remainingItems,
                    trashedCount = trashedItems.size,
                    trashedBytes = trashedItems.sumOf { it.sizeBytes },
                    keptCount = keptItems.size,
                    favoritedCount = favoritedItems.size,
                    privacyActive = privacyActive,
                    onTogglePrivacy = { viewModel.togglePrivacyMode() },
                    selectedFilter = selectedFilter,
                    currentSortOrder = currentSortOrder,
                    onSortOrderChanged = { viewModel.setSortOrder(it) },
                    onSwipeKeep = { viewModel.handleSwipeKeep(it, context) },
                    onSwipeTrash = { viewModel.handleSwipeTrash(it, context) },
                    onSwipeFavorite = { viewModel.handleSwipeFavorite(it, context) },
                    onUndo = { viewModel.undoLastAction(context) },
                    canUndo = viewModel.canUndo(),
                    onEmptyTrash = {
                        viewModel.emptyAndPurgeTrash(context) { pendingIntent ->
                            try {
                                val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                                deleteIntentLauncher.launch(intentSenderRequest)
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Failed to launch bulk delete: ${e.message}")
                            }
                        }
                    },
                    onBackToFilters = { viewModel.returnToFilterSelection(context, isPermissionGranted) },
                    trashedItems = trashedItems,
                    onRestoreFromTrash = { viewModel.restoreFromTrash(it, context) },
                    onDeletePermanentlyFromTrash = { item ->
                        viewModel.deletePermanentlyFromTrash(item, context) { pendingIntent ->
                            try {
                                val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                                deleteIntentLauncher.launch(intentSenderRequest)
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Failed to launch single delete: ${e.message}")
                            }
                        }
                    }
                )
            }
            is AppUiState.SuccessCelebration -> {
                SuccessCelebration(
                    freedBytes = state.freedBytes,
                    itemCount = state.itemCountCount,
                    onFinish = { viewModel.returnToFilterSelection(context, isPermissionGranted) }
                )
            }
        }
    }
}

/**
 * Whether the app can read the gallery — either full access (images + video) or, on Android 14+,
 * partial access via the "selected photos" permission. Returns false only when nothing is granted.
 */
private fun hasGalleryAccess(context: Context): Boolean {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            (granted(Manifest.permission.READ_MEDIA_IMAGES) && granted(Manifest.permission.READ_MEDIA_VIDEO)) ||
                granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            granted(Manifest.permission.READ_MEDIA_IMAGES) && granted(Manifest.permission.READ_MEDIA_VIDEO)
        else ->
            granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}
