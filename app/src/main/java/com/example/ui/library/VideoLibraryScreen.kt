package com.example.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.model.VideoItem
import com.example.core.util.FormatUtils
import com.example.core.util.PermissionUtils
import com.example.ui.components.VideoThumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoLibraryScreen(
    modifier: Modifier = Modifier,
    viewModel: VideoLibraryViewModel = viewModel(),
    onVideoClick: (VideoItem) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var isSearchActive by remember { mutableStateOf(false) }
    var isSortMenuExpanded by remember { mutableStateOf(false) }

    // Launcher for Android runtime storage permission
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onPermissionResult(isGranted)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            placeholder = { Text("Search videos...") },
                            singleLine = true,
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.onSearchQueryChanged("") },
                                        modifier = Modifier.testTag("clear_search_button")
                                    ) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Clear Search")
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_text_field")
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.VideoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "FoxPlayer",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                },
                actions = {
                    // Search toggle button
                    IconButton(
                        onClick = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) viewModel.onSearchQueryChanged("")
                        },
                        modifier = Modifier.testTag("search_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = if (isSearchActive) "Close Search" else "Search Videos"
                        )
                    }

                    // Sort menu button
                    Box {
                        IconButton(
                            onClick = { isSortMenuExpanded = true },
                            modifier = Modifier.testTag("sort_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Sort,
                                contentDescription = "Sort Options"
                            )
                        }

                        DropdownMenu(
                            expanded = isSortMenuExpanded,
                            onDismissRequest = { isSortMenuExpanded = false }
                        ) {
                            VideoSortOption.values().forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.displayName,
                                            fontWeight = if (uiState.sortOption == option) FontWeight.Bold else FontWeight.Normal,
                                            color = if (uiState.sortOption == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        viewModel.onSortOptionChanged(option)
                                        isSortMenuExpanded = false
                                    },
                                    modifier = Modifier.testTag("sort_option_${option.name}")
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            when {
                // 1. Permission Denied State
                !uiState.isPermissionGranted -> {
                    PermissionDeniedView(
                        onRequestPermission = {
                            permissionLauncher.launch(PermissionUtils.getRequiredVideoPermission())
                        }
                    )
                }

                // 2. Loading State
                uiState.isLoading -> {
                    LoadingView()
                }

                // 3. Error State
                uiState.errorMessage != null -> {
                    ErrorView(
                        message = uiState.errorMessage ?: "Unknown error occurred",
                        onRetry = { viewModel.refresh() }
                    )
                }

                // 4. Empty State
                uiState.isEmpty -> {
                    EmptyLibraryView(
                        isSearching = uiState.searchQuery.isNotBlank(),
                        onRefresh = { viewModel.refresh() }
                    )
                }

                // 5. Success State: Lazy loaded video list
                else -> {
                    VideoListView(
                        videos = uiState.videos,
                        onVideoClick = onVideoClick
                    )
                }
            }
        }
    }
}

/**
 * Optimized video list using LazyColumn with unique keys for zero-jank scrolling on mid-range devices.
 */
@Composable
private fun VideoListView(
    videos: List<VideoItem>,
    onVideoClick: (VideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("video_lazy_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = videos,
            key = { video -> video.id }
        ) { video ->
            VideoCardItem(
                video = video,
                onClick = { onVideoClick(video) }
            )
        }
    }
}

/**
 * Individual video item card showing thumbnail, duration badge, title, and metadata.
 */
@Composable
private fun VideoCardItem(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("video_card_${video.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Video Thumbnail with Duration Badge
            VideoThumbnail(
                uri = video.uri,
                durationMs = video.durationMs,
                contentDescription = video.title,
                modifier = Modifier
                    .size(width = 130.dp, height = 78.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            // Video Details (Title, Folder, Size & Date)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp)
            ) {
                Text(
                    text = video.title.ifBlank { video.displayName },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 20.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Folder & Resolution label
                val folderLabel = video.bucketName.ifBlank { "Videos" }
                val resolutionLabel = if (video.width > 0 && video.height > 0) {
                    "${video.width}x${video.height}"
                } else null

                val subInfo = buildString {
                    append(folderLabel)
                    if (resolutionLabel != null) {
                        append(" • ")
                        append(resolutionLabel)
                    }
                }

                Text(
                    text = subInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                // File size and modified date
                val metaText = buildString {
                    append(FormatUtils.formatFileSize(video.sizeBytes))
                    val dateFormatted = FormatUtils.formatDate(video.dateModified)
                    if (dateFormatted.isNotBlank()) {
                        append(" • ")
                        append(dateFormatted)
                    }
                }

                Text(
                    text = metaText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Full-screen loading indicator.
 */
@Composable
private fun LoadingView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("loading_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            strokeWidth = 4.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Scanning videos...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * View displayed when video storage permission has not been granted.
 */
@Composable
private fun PermissionDeniedView(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
            .testTag("permission_denied_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Storage Permission Required",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "FoxPlayer needs permission to scan and display your local video collection.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.testTag("grant_permission_button")
        ) {
            Text("Grant Permission")
        }
    }
}

/**
 * View displayed when the device has no videos or search results are empty.
 */
@Composable
private fun EmptyLibraryView(
    isSearching: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
            .testTag("empty_library_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isSearching) "No matching videos" else "No videos found",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isSearching) "Try a different search keyword" else "No video files were detected in your device storage.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.testTag("refresh_library_button")
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Refresh")
        }
    }
}

/**
 * View displayed when an error occurs while reading videos.
 */
@Composable
private fun ErrorView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
            .testTag("error_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.testTag("retry_button")
        ) {
            Text("Retry")
        }
    }
}
