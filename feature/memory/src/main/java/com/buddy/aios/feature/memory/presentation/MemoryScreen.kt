package com.buddy.aios.feature.memory.presentation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buddy.aios.core.domain.entity.Memory
import com.buddy.aios.core.ui.components.AIOSButton
import com.buddy.aios.core.ui.components.AIOSChip
import com.buddy.aios.core.ui.components.AIOSLoadingIndicator
import com.buddy.aios.core.ui.components.GlassCard
import com.buddy.aios.core.ui.shapes.BuddyShapes
import com.buddy.aios.core.ui.theme.BuddyColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: MemoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var selectedCategory by remember { mutableStateOf("All") }
    var memoryToDelete by remember { mutableStateOf<Memory?>(null) }
    var memoryToEdit by remember { mutableStateOf<Memory?>(null) }
    var editedContent by remember { mutableStateOf("") }

    val categories = listOf("All", "Preferences", "Goals", "Projects", "Routines", "Important")

    val filteredMemories = remember(state.memories, searchQuery, selectedCategory) {
        state.memories.filter { memory ->
            val matchesSearch = searchQuery.isBlank() || memory.summary.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategory == "All" || when (selectedCategory) {
                "Preferences" -> memory.summary.contains("prefer", true) || memory.summary.contains("like", true) || memory.summary.contains("favourite", true)
                "Goals"       -> memory.summary.contains("goal", true) || memory.summary.contains("plan", true) || memory.summary.contains("want to", true)
                "Projects"    -> memory.summary.contains("project", true) || memory.summary.contains("code", true) || memory.summary.contains("build", true)
                "Routines"    -> memory.summary.contains("daily", true) || memory.summary.contains("usually", true) || memory.summary.contains("always", true)
                "Important"   -> memory.importance >= 0.7f
                else -> true
            }
            matchesSearch && matchesCategory
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BuddyColors.BackgroundDeep)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("AIOS remembers", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            Text("Your personal long-term memory vault", style = MaterialTheme.typography.labelSmall, color = BuddyColors.TextSecondary)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BuddyColors.SurfaceDark.copy(alpha = 0.85f)),
                )
            },
            containerColor = Color.Transparent,
        ) { padding ->
            if (state.isLoading) {
                AIOSLoadingIndicator()
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 20.dp),
                ) {
                    Spacer(Modifier.height(16.dp))

                    // Summary Vault Metric Banner
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = BuddyColors.SurfaceDark.copy(alpha = 0.90f),
                        borderBrush = BuddyColors.GlassCardBorderGradient
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = BuddyColors.Cyan,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "MEMORY VAULT",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = BuddyColors.Cyan
                                    )
                                    Text(
                                        text = "${state.memories.size} active memories stored locally",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::onSearchQueryChanged,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search memory vault...", color = BuddyColors.TextMuted) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = BuddyColors.Cyan) },
                        shape = BuddyShapes.Pill,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BuddyColors.Cyan,
                            unfocusedBorderColor = BuddyColors.GlassBorder,
                            focusedContainerColor = BuddyColors.GlassSurface,
                            unfocusedContainerColor = BuddyColors.GlassSurface,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                        )
                    )

                    Spacer(Modifier.height(16.dp))

                    // Category Filters
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categories) { category ->
                            AIOSChip(
                                text = category,
                                isSelected = selectedCategory == category,
                                onClick = { selectedCategory = category }
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    if (filteredMemories.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Memory, contentDescription = null, tint = BuddyColors.TextMuted, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("AIOS hasn't learned anything here yet.", style = MaterialTheme.typography.titleMedium, color = BuddyColors.TextSecondary)
                                Spacer(Modifier.height(4.dp))
                                Text("Tell AIOS something worth remembering.", style = MaterialTheme.typography.bodySmall, color = BuddyColors.TextMuted)
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 100.dp)
                        ) {
                            items(filteredMemories, key = { it.id }) { memory ->
                                MemoryCard(
                                    memory = memory,
                                    onEdit = {
                                        memoryToEdit = memory
                                        editedContent = memory.summary
                                    },
                                    onDelete = { memoryToDelete = memory }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Dialog
    if (memoryToDelete != null) {
        AlertDialog(
            onDismissRequest = { memoryToDelete = null },
            title = { Text("Forget Memory?", color = Color.White) },
            text = { Text("AIOS will no longer remember this detail in future conversations.", color = BuddyColors.TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        memoryToDelete?.let { viewModel.onDeleteMemory(it.id) }
                        memoryToDelete = null
                    }
                ) {
                    Text("Forget", color = BuddyColors.Rose, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { memoryToDelete = null }) {
                    Text("Cancel", color = BuddyColors.TextMuted)
                }
            },
            containerColor = BuddyColors.SurfaceDark
        )
    }

    // Edit Bottom Sheet
    if (memoryToEdit != null) {
        ModalBottomSheet(
            onDismissRequest = { memoryToEdit = null },
            containerColor = BuddyColors.SurfaceDark
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text("Edit Memory", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = editedContent,
                    onValueChange = { editedContent = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BuddyColors.Cyan,
                        unfocusedBorderColor = BuddyColors.GlassBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    )
                )
                Spacer(Modifier.height(20.dp))
                AIOSButton(
                    text = "Save Memory",
                    onClick = {
                        memoryToEdit?.let { viewModel.onUpdateMemory(it.copy(summary = editedContent.trim())) }
                        memoryToEdit = null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun MemoryCard(
    memory: Memory,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateStr = remember(memory.createdAt) {
        SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).format(Date(memory.createdAt))
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = BuddyColors.SurfaceDark.copy(alpha = 0.80f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = memory.summary,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dateStr, style = MaterialTheme.typography.labelSmall, color = BuddyColors.TextMuted)
                    Spacer(Modifier.width(8.dp))
                    Text("•", style = MaterialTheme.typography.labelSmall, color = BuddyColors.TextMuted)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Importance ${(memory.importance * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = BuddyColors.PurpleLight
                    )
                }
            }

            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = BuddyColors.Cyan, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = BuddyColors.Rose.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
