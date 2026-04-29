package com.auak.agent.feature.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auak.agent.R
import com.auak.agent.core.settings.AppToolInfo
import com.auak.agent.core.settings.AppToolManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppToolsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { AppToolManager(context) }

    val categories = remember { manager.getCategories() }

    val defaults = remember {
        mutableStateMapOf<String, String>().apply {
            for (category in categories.keys) {
                val pkg = manager.getDefaultApp(category)
                if (pkg != null) put(category, pkg)
            }
        }
    }

    var expandedCategory by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_tools_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        if (categories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.app_tools_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val sortedCategories = remember(categories) {
                // Sort by CATEGORY_NAMES order
                val order = AppToolManager.CATEGORY_NAMES.keys.toList()
                categories.entries.sortedBy { (cat, _) ->
                    val idx = order.indexOf(cat)
                    if (idx >= 0) idx else Int.MAX_VALUE
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sortedCategories, key = { it.key }) { (category, apps) ->
                    val defaultPkg = defaults[category]
                    val defaultApp = apps.find { it.packageName == defaultPkg }
                    val isExpanded = expandedCategory == category

                    CategoryCard(
                        categoryName = category,
                        defaultApp = defaultApp,
                        appCount = apps.size,
                        isExpanded = isExpanded,
                        onToggle = {
                            expandedCategory = if (isExpanded) null else category
                        },
                        apps = apps,
                        selectedPkg = defaultPkg,
                        onSelectApp = { pkg ->
                            manager.setDefaultApp(category, pkg)
                            defaults[category] = pkg
                            expandedCategory = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    categoryName: String,
    defaultApp: AppToolInfo?,
    appCount: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    apps: List<AppToolInfo>,
    selectedPkg: String?,
    onSelectApp: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        onClick = onToggle
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(categoryName, fontWeight = FontWeight.SemiBold)
                    if (defaultApp != null) {
                        Text(
                            stringResource(R.string.app_tools_default, defaultApp.label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    stringResource(R.string.app_tools_count, appCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isExpanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                apps.forEach { app ->
                    val isSelected = app.packageName == selectedPkg
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectApp(app.packageName) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            app.label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
