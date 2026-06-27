/**
 * MakClean - Gamified Gallery Cleaner
 * Developed by/Author: schwarmak-dev (https://github.com/schwarmak-dev)
 * 
 * Copyright (c) 2026 schwarmak-dev. All rights reserved.
 */
package com.example.ui

import android.app.Application
import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.media.MediaFilterType
import com.example.media.MediaItem
import com.example.media.MediaScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class AppUiState {
    object FilterSelection : AppUiState()
    object Scanning : AppUiState()
    object DeckActive : AppUiState()
    data class SuccessCelebration(val freedBytes: Long, val itemCountCount: Int) : AppUiState()
}

enum class DeckSortOrder {
    LARGEST_FIRST,
    OLDEST_FIRST,
    NEWEST_FIRST
}

enum class MakCleanTheme(val displayName: String) {
    NEON_CYBER("Cyberpunk"),
    WARM_ORGANIC("Cálido"),
    NORDIC_ROSE("Aesthetic"),
    CLASSIC_SLATE("Elegante")
}

data class StorageCategoryStats(
    val type: MediaFilterType,
    val sizeBytes: Long,
    val count: Int,
    val colorHex: Long
)

data class StorageAnalytics(
    val totalCleanableBytes: Long,
    val categories: List<StorageCategoryStats>
)

data class SwipeAction(
    val item: MediaItem,
    val type: ActionType
)

enum class ActionType {
    KEEP, TRASH, FAVORITE
}

// Upper bound for the persisted "already removed" id set, so it can't grow forever.
private const val MAX_DELETED_IDS = 5000

class MakCleanViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MakCleanViewModel"

    // App-wide preferences, used to persist the theme, privacy mode and deleted-id list.
    private val appPrefs = application.getSharedPreferences("makclean_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow<AppUiState>(AppUiState.FilterSelection)
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _selectedFilter = MutableStateFlow(MediaFilterType.ALL)
    val selectedFilter: StateFlow<MediaFilterType> = _selectedFilter.asStateFlow()

    // Theme is persisted so the user's choice survives app restarts.
    private val _activeTheme = MutableStateFlow(loadPersistedTheme())
    val activeTheme: StateFlow<MakCleanTheme> = _activeTheme.asStateFlow()

    fun setTheme(theme: MakCleanTheme) {
        _activeTheme.value = theme
        appPrefs.edit().putString("active_theme", theme.name).apply()
    }

    private val _remainingItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val remainingItems: StateFlow<List<MediaItem>> = _remainingItems.asStateFlow()

    private val _keptItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val keptItems: StateFlow<List<MediaItem>> = _keptItems.asStateFlow()

    private val _trashedItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val trashedItems: StateFlow<List<MediaItem>> = _trashedItems.asStateFlow()

    private val _favoritedItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val favoritedItems: StateFlow<List<MediaItem>> = _favoritedItems.asStateFlow()

    private val actionHistory = mutableListOf<SwipeAction>()

    private var itemsPendingDeletion = listOf<MediaItem>()
    private var isPurgeAllOperation = false
    private var lastFreedSizeBytes = 0L
    private var lastDeletedCount = 0

    // Privacy lock (blurs visual output until long-pressed). Persisted across launches.
    private val _privacyMode = MutableStateFlow(appPrefs.getBoolean("privacy_mode", false))
    val privacyMode: StateFlow<Boolean> = _privacyMode.asStateFlow()

    private val _deckSortOrder = MutableStateFlow(DeckSortOrder.NEWEST_FIRST)
    val deckSortOrder: StateFlow<DeckSortOrder> = _deckSortOrder.asStateFlow()

    private val _storageAnalytics = MutableStateFlow<StorageAnalytics?>(null)
    val storageAnalytics: StateFlow<StorageAnalytics?> = _storageAnalytics.asStateFlow()

    fun setFilter(filter: MediaFilterType) {
        _selectedFilter.value = filter
    }

    fun togglePrivacyMode() {
        val enabled = !_privacyMode.value
        _privacyMode.value = enabled
        appPrefs.edit().putBoolean("privacy_mode", enabled).apply()
    }

    private fun loadPersistedTheme(): MakCleanTheme {
        val saved = appPrefs.getString("active_theme", null) ?: return MakCleanTheme.CLASSIC_SLATE
        return runCatching { MakCleanTheme.valueOf(saved) }.getOrDefault(MakCleanTheme.CLASSIC_SLATE)
    }

    // Returns whether a media item matches the currently selected filter.
    // Mirrors MediaScanner.shouldInclude so mock and real data are filtered identically.
    private fun matchesSelectedFilter(item: MediaItem): Boolean {
        return when (_selectedFilter.value) {
            MediaFilterType.ALL -> true
            MediaFilterType.SCREENSHOTS -> item.type == MediaFilterType.SCREENSHOTS
            MediaFilterType.HEAVY_VIDEOS -> item.type == MediaFilterType.HEAVY_VIDEOS
            MediaFilterType.DUPLICATES -> item.isDuplicate
            MediaFilterType.BLURRY -> item.isBlurred
            MediaFilterType.GIFS -> item.type == MediaFilterType.GIFS || item.title.endsWith(".gif", ignoreCase = true)
            MediaFilterType.LIGHT_VIDEOS -> item.type == MediaFilterType.LIGHT_VIDEOS
        }
    }

    // Launch scan of selected configuration
    fun startScan(context: Context, hasPermission: Boolean) {
        viewModelScope.launch {
            _uiState.value = AppUiState.Scanning
            try {
                val scanned = if (hasPermission) {
                    withContext(Dispatchers.IO) {
                        MediaScanner.scanDeviceGallery(context, _selectedFilter.value)
                    }
                } else {
                    emptyList()
                }

                // Fallback to high-quality mock data ONLY if permissions are denied or mock is requested for a completely blank emulator
                val finalMedia = if (hasPermission) {
                    // Only run the extra (expensive) full-gallery checks when the filtered scan came back empty.
                    // If we already found matching media there is no need to re-scan the whole device.
                    if (scanned.isNotEmpty()) {
                        scanned
                    } else {
                        val hasDeletedFiles = withContext(Dispatchers.IO) {
                            getDeletedIds(context).isNotEmpty()
                        }
                        val isEmptyGallery = withContext(Dispatchers.IO) {
                            MediaScanner.scanDeviceGallery(context, MediaFilterType.ALL).isEmpty()
                        }
                        // Only provide mock demo items if the entire gallery of the device has absolutely zero media files of any kind
                        // AND the user hasn't completed any deletions yet (so they enjoy the premium tour on a sterile app emulator)
                        if (!hasDeletedFiles && isEmptyGallery) {
                            MediaScanner.getMockMediaItems().filter { matchesSelectedFilter(it) }
                        } else {
                            scanned
                        }
                    }
                } else {
                    MediaScanner.getMockMediaItems().filter { matchesSelectedFilter(it) }
                }

                // Filter out permanently deleted items from both real and mock lists
                val deletedIds = withContext(Dispatchers.IO) {
                    getDeletedIds(context)
                }
                val filteredMedia = finalMedia.filter { item ->
                    !deletedIds.contains(item.id.toString())
                }

                _remainingItems.value = filteredMedia
                applyCurrentSort()
                _keptItems.value = emptyList()
                _trashedItems.value = emptyList()
                _favoritedItems.value = emptyList()
                actionHistory.clear()
            } catch (t: Throwable) {
                // Never get stuck on the "Analizando" screen — show an (empty) deck instead.
                Log.e(TAG, "startScan failed: ${t.message}", t)
                _remainingItems.value = emptyList()
                _keptItems.value = emptyList()
                _trashedItems.value = emptyList()
                _favoritedItems.value = emptyList()
                actionHistory.clear()
            } finally {
                _uiState.value = AppUiState.DeckActive
            }
        }
    }

    // Swipe card actions
    fun handleSwipeKeep(item: MediaItem, context: Context) {
        triggerHaptic(context, HapticType.SOFT)
        _remainingItems.value = _remainingItems.value.filter { it.id != item.id }
        _keptItems.value = _keptItems.value + item
        actionHistory.add(SwipeAction(item, ActionType.KEEP))
    }

    fun handleSwipeTrash(item: MediaItem, context: Context) {
        triggerHaptic(context, HapticType.MEDIUM)
        _remainingItems.value = _remainingItems.value.filter { it.id != item.id }
        _trashedItems.value = _trashedItems.value + item
        actionHistory.add(SwipeAction(item, ActionType.TRASH))
    }

    fun handleSwipeFavorite(item: MediaItem, context: Context) {
        triggerHaptic(context, HapticType.DOUBLE)
        _remainingItems.value = _remainingItems.value.filter { it.id != item.id }
        _favoritedItems.value = _favoritedItems.value + item
        actionHistory.add(SwipeAction(item, ActionType.FAVORITE))
    }

    fun restoreFromTrash(item: MediaItem, context: Context) {
        _trashedItems.value = _trashedItems.value.filter { it.id != item.id }
        _remainingItems.value = listOf(item) + _remainingItems.value
        actionHistory.removeAll { it.item.id == item.id && it.type == ActionType.TRASH }
        triggerHaptic(context, HapticType.LIGHT)
    }

    private fun getDeletedIds(context: Context): Set<String> {
        val prefs = context.getSharedPreferences("makclean_prefs", Context.MODE_PRIVATE)
        return prefs.getStringSet("deleted_media_ids", emptySet()) ?: emptySet()
    }

    private fun addDeletedIds(context: Context, ids: List<Long>) {
        if (ids.isEmpty()) return
        val prefs = context.getSharedPreferences("makclean_prefs", Context.MODE_PRIVATE)
        val currentDeleted = prefs.getStringSet("deleted_media_ids", emptySet()) ?: emptySet()
        val updated = currentDeleted.toMutableSet()
        updated.addAll(ids.map { it.toString() })
        // Soft cap to keep this set from growing without bound across the app's lifetime.
        val capped = if (updated.size > MAX_DELETED_IDS) {
            updated.toList().takeLast(MAX_DELETED_IDS).toSet()
        } else {
            updated
        }
        prefs.edit().putStringSet("deleted_media_ids", capped).apply()
    }

    // Callbacks for Activity result
    fun onDeletionConfirmed(context: Context) {
        viewModelScope.launch {
            triggerHaptic(context, HapticType.HEAVY)
            
            withContext(Dispatchers.IO) {
                // The OS dialog already moved the items to the system trash; we must NOT delete
                // them again here (that would purge them permanently). Just record their ids so
                // they are excluded from future scans.
                addDeletedIds(context, itemsPendingDeletion.map { it.id })
            }

            if (isPurgeAllOperation) {
                // Clear all trashed items
                _trashedItems.value = emptyList()
                _uiState.value = AppUiState.SuccessCelebration(freedBytes = lastFreedSizeBytes, itemCountCount = lastDeletedCount)
            } else {
                // Single item deletion from trash view
                _trashedItems.value = _trashedItems.value.filter { item -> !itemsPendingDeletion.any { pending -> pending.id == item.id } }
            }

            // Reset pending fields
            itemsPendingDeletion = emptyList()
            isPurgeAllOperation = false
            lastFreedSizeBytes = 0L
            lastDeletedCount = 0

            val hasPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            loadStorageAnalytics(context, hasPerm)
        }
    }

    fun onDeletionCancelled(context: Context) {
        // Reset pending fields
        itemsPendingDeletion = emptyList()
        isPurgeAllOperation = false
        lastFreedSizeBytes = 0L
        lastDeletedCount = 0
        triggerHaptic(context, HapticType.LIGHT)
    }

    private fun confirmDeletionSuccess(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                for (item in itemsPendingDeletion) {
                    try {
                        context.contentResolver.delete(item.uri, null, null)
                    } catch (t: Throwable) {
                        Log.e(TAG, "confirmDeletionSuccess: ContentResolver delete failed: ${t.message}")
                    }
                }

                addDeletedIds(context, itemsPendingDeletion.map { it.id })
            }

            if (isPurgeAllOperation) {
                _trashedItems.value = emptyList()
                _uiState.value = AppUiState.SuccessCelebration(freedBytes = lastFreedSizeBytes, itemCountCount = lastDeletedCount)
            } else {
                _trashedItems.value = _trashedItems.value.filter { item -> !itemsPendingDeletion.any { pending -> pending.id == item.id } }
            }

            itemsPendingDeletion = emptyList()
            isPurgeAllOperation = false
            lastFreedSizeBytes = 0L
            lastDeletedCount = 0

            val hasPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            loadStorageAnalytics(context, hasPerm)
        }
    }

    private fun performLegacyDelete(context: Context, items: List<MediaItem>, onLaunchIntent: (PendingIntent) -> Unit) {
        viewModelScope.launch {
            for (item in items) {
                val needConfirmation = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.delete(item.uri, null, null)
                        false
                    } catch (securityException: SecurityException) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val recoverable = securityException as? RecoverableSecurityException
                            if (recoverable != null) {
                                true to recoverable.userAction.actionIntent
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error in legacy delete loop for uri ${item.uri}: ${t.message}")
                        false
                    }
                }

                if (needConfirmation is Pair<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    onLaunchIntent(needConfirmation.second as PendingIntent)
                    return@launch // stop and wait for UI to handle confirmation
                }
            }
            // If we finished without being handled by OS dialog, consider it succeeded
            confirmDeletionSuccess(context)
        }
    }

    private fun performDirectDeleteNoPrompt(context: Context, items: List<MediaItem>) {
        for (item in items) {
            try {
                context.contentResolver.delete(item.uri, null, null)
            } catch (t: Throwable) {
                Log.e(TAG, "Direct delete failed for uri ${item.uri}: ${t.message}")
            }
        }
    }

    fun deletePermanentlyFromTrash(item: MediaItem, context: Context, onLaunchIntent: (PendingIntent) -> Unit) {
        viewModelScope.launch {
            if (item.uri.scheme == "content") {
                itemsPendingDeletion = listOf(item)
                isPurgeAllOperation = false
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        // Move to the system trash (recoverable ~30 days) instead of deleting forever.
                        val pendingIntent = withContext(Dispatchers.IO) {
                            MediaStore.createTrashRequest(context.contentResolver, listOf(item.uri), true)
                        }
                        onLaunchIntent(pendingIntent)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error creating single trash request: ${t.message}")
                        performLegacyDelete(context, listOf(item), onLaunchIntent)
                    }
                } else {
                    performLegacyDelete(context, listOf(item), onLaunchIntent)
                }
            } else {
                // Mock item
                _trashedItems.value = _trashedItems.value.filter { it.id != item.id }
                withContext(Dispatchers.IO) {
                    addDeletedIds(context, listOf(item.id))
                }
                triggerHaptic(context, HapticType.MEDIUM)
            }
        }
    }

    // Undo safety action (Feature 2 & 1)
    fun undoLastAction(context: Context) {
        if (actionHistory.isEmpty()) return
        triggerHaptic(context, HapticType.LIGHT)
        
        val lastAction = actionHistory.removeAt(actionHistory.size - 1)
        val item = lastAction.item

        // Remove from its destination list
        when (lastAction.type) {
            ActionType.KEEP -> _keptItems.value = _keptItems.value.filter { it.id != item.id }
            ActionType.TRASH -> _trashedItems.value = _trashedItems.value.filter { it.id != item.id }
            ActionType.FAVORITE -> _favoritedItems.value = _favoritedItems.value.filter { it.id != item.id }
        }

        // Return to the front of the remaining list
        _remainingItems.value = listOf(item) + _remainingItems.value
    }

    fun canUndo(): Boolean {
        return actionHistory.isNotEmpty()
    }

    // Execute definitivo of items in trash + Dopamine Success Splash
    fun emptyAndPurgeTrash(context: Context, onLaunchIntent: (PendingIntent) -> Unit) {
        viewModelScope.launch {
            val bytesFreed = trashedItems.value.sumOf { it.sizeBytes }
            val count = trashedItems.value.size

            if (count == 0) {
                // Nothing in the trash means no space is actually freed, so skip the
                // "space recovered" celebration (and its heavy haptic) and just go back.
                _uiState.value = AppUiState.FilterSelection
                return@launch
            }

            val realItems = trashedItems.value.filter { it.uri.scheme == "content" }
            val mockItems = trashedItems.value.filter { it.uri.scheme != "content" }

            if (realItems.isNotEmpty()) {
                itemsPendingDeletion = realItems
                isPurgeAllOperation = true
                lastFreedSizeBytes = bytesFreed
                lastDeletedCount = count

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        // Move to the system trash (recoverable ~30 days) instead of deleting forever.
                        val pendingIntent = withContext(Dispatchers.IO) {
                            MediaStore.createTrashRequest(context.contentResolver, realItems.map { it.uri }, true)
                        }
                        onLaunchIntent(pendingIntent)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error creating bulk trash request on Android R+: ${t.message}")
                        performLegacyDelete(context, realItems, onLaunchIntent)
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    performLegacyDelete(context, realItems, onLaunchIntent)
                } else {
                    withContext(Dispatchers.IO) {
                        performDirectDeleteNoPrompt(context, realItems)
                    }
                    confirmDeletionSuccess(context)
                }
            } else {
                // Only mock items
                withContext(Dispatchers.IO) {
                    addDeletedIds(context, mockItems.map { it.id })
                }
                _trashedItems.value = emptyList()
                _uiState.value = AppUiState.SuccessCelebration(freedBytes = bytesFreed, itemCountCount = count)
                triggerHaptic(context, HapticType.HEAVY)
            }
        }
    }

    fun returnToFilterSelection(context: Context, hasPermission: Boolean) {
        _uiState.value = AppUiState.FilterSelection
        _remainingItems.value = emptyList()
        _keptItems.value = emptyList()
        _trashedItems.value = emptyList()
        _favoritedItems.value = emptyList()
        actionHistory.clear()
        loadStorageAnalytics(context, hasPermission)
    }

    fun setSortOrder(order: DeckSortOrder) {
        _deckSortOrder.value = order
        applyCurrentSort()
    }

    private fun applyCurrentSort() {
        val currentList = _remainingItems.value.toMutableList()
        when (_deckSortOrder.value) {
            DeckSortOrder.LARGEST_FIRST -> currentList.sortByDescending { it.sizeBytes }
            DeckSortOrder.OLDEST_FIRST -> currentList.sortBy { it.dateAdded }
            DeckSortOrder.NEWEST_FIRST -> currentList.sortByDescending { it.dateAdded }
        }
        _remainingItems.value = currentList
    }

    fun loadStorageAnalytics(context: Context, hasPermission: Boolean) {
        viewModelScope.launch {
            // Phase 1 — instant: only metadata-based categories (videos, screenshots, GIFs).
            // No per-file hashing or bitmap decoding here, so the filter screen never blocks.
            var usingRealData = false
            val base = withContext(Dispatchers.IO) {
                try {
                    val scanned = if (hasPermission) {
                        MediaScanner.scanDeviceGallery(context, MediaFilterType.ALL)
                    } else {
                        emptyList()
                    }

                    val hasDeletedFiles = getDeletedIds(context).isNotEmpty()
                    usingRealData = hasPermission && scanned.isNotEmpty()

                    val finalItems = if (scanned.isEmpty() && !hasDeletedFiles) {
                        MediaScanner.getMockMediaItems()
                    } else {
                        scanned
                    }

                    val deletedIds = getDeletedIds(context)
                    val allItems = finalItems.filter { !deletedIds.contains(it.id.toString()) }

                    fun sizeOf(predicate: (MediaItem) -> Boolean) = allItems.filter(predicate).sumOf { it.sizeBytes }
                    fun countOf(predicate: (MediaItem) -> Boolean) = allItems.count(predicate)

                    // For real galleries, duplicates/blur start at the mock-derived values (0 for real
                    // data) and are filled in by the background pass / their own filter on demand.
                    val categoryStatsList = listOf(
                        StorageCategoryStats(MediaFilterType.DUPLICATES, sizeOf { it.isDuplicate }, countOf { it.isDuplicate }, 0xFF10B981),
                        StorageCategoryStats(MediaFilterType.HEAVY_VIDEOS, sizeOf { it.type == MediaFilterType.HEAVY_VIDEOS }, countOf { it.type == MediaFilterType.HEAVY_VIDEOS }, 0xFFF59E0B),
                        StorageCategoryStats(MediaFilterType.SCREENSHOTS, sizeOf { it.type == MediaFilterType.SCREENSHOTS }, countOf { it.type == MediaFilterType.SCREENSHOTS }, 0xFF60A5FA),
                        StorageCategoryStats(MediaFilterType.BLURRY, sizeOf { it.isBlurred }, countOf { it.isBlurred }, 0xFF8B5CF6),
                        StorageCategoryStats(MediaFilterType.GIFS, sizeOf { it.type == MediaFilterType.GIFS }, countOf { it.type == MediaFilterType.GIFS }, 0xFFF43F5E),
                        StorageCategoryStats(MediaFilterType.LIGHT_VIDEOS, sizeOf { it.type == MediaFilterType.LIGHT_VIDEOS }, countOf { it.type == MediaFilterType.LIGHT_VIDEOS }, 0xFF00E5FF)
                    )

                    StorageAnalytics(
                        totalCleanableBytes = categoryStatsList.sumOf { it.sizeBytes },
                        categories = categoryStatsList
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error calculating storage analytics: ${e.message}", e)
                    null
                }
            }
            _storageAnalytics.value = base

            // Phase 2 — background: real duplicate detection fills in the "Duplicados" figure
            // without blocking the screen. Blur stays out (it is computed only inside its filter).
            if (base != null && usingRealData) {
                withContext(Dispatchers.IO) {
                    try {
                        val deletedIds = getDeletedIds(context)
                        val dups = MediaScanner.scanDeviceGallery(context, MediaFilterType.DUPLICATES)
                            .filter { !deletedIds.contains(it.id.toString()) }
                        val dupSize = dups.sumOf { it.sizeBytes }

                        val updatedCategories = base.categories.map {
                            if (it.type == MediaFilterType.DUPLICATES) it.copy(sizeBytes = dupSize, count = dups.size) else it
                        }
                        _storageAnalytics.value = base.copy(
                            categories = updatedCategories,
                            totalCleanableBytes = base.totalCleanableBytes + dupSize
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error computing duplicate analytics: ${e.message}", e)
                    }
                }
            }
        }
    }

    // Modern Haptic Service callers with absolute crash safety and full backward compatibility
    private fun triggerHaptic(context: Context, type: HapticType) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = when (type) {
                        HapticType.LIGHT -> VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)
                        HapticType.SOFT -> VibrationEffect.createOneShot(35, 100)
                        HapticType.MEDIUM -> VibrationEffect.createOneShot(60, 180)
                        HapticType.HEAVY -> @Suppress("DEPRECATION") VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 200), intArrayOf(0, 255, 0, 255), -1)
                        HapticType.DOUBLE -> @Suppress("DEPRECATION") VibrationEffect.createWaveform(longArrayOf(0, 30, 40, 30), intArrayOf(0, 150, 0, 150), -1)
                    }
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(if (type == HapticType.HEAVY) 300L else 50L)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed haptic vibration safely: ${t.message}")
        }
    }

    enum class HapticType {
        LIGHT, SOFT, MEDIUM, HEAVY, DOUBLE
    }
}
