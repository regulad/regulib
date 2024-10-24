package xyz.regulad.regulib.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A composable that displays a grid of items with a dynamic layout.
 *
 * Layout behavior:
 * - 1 item: fills the entire space
 * - 2+ items: creates a centered grid layout with number of columns determined by sqrt of item count,
 *   maintaining parent's aspect ratio
 *
 * This DynamicGridLayout, the [DynamicColumnRowGridLayout], creates columns containing rows. For certain use cases, like 9:16 video, [DynamicRowColumnGridLayout] may be more appropriate.
 */
@Composable
@Suppress("Unused")
fun <T> DynamicColumnRowGridLayout(
    modifier: Modifier = Modifier,
    items: List<T>,
    itemContent: @Composable (T, Modifier) -> Unit
) {
    Box(modifier = modifier) {
        when {
            items.isEmpty() -> return@Box
            items.size == 1 -> {
                itemContent(items[0], Modifier.fillMaxSize())
            }

            else -> {
                val columns = sqrt(items.size.toFloat()).roundToInt()
                val rows = (items.size + columns - 1) / columns

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        for (row in 0 until rows) {
                            val startIndex = row * columns
                            val rowItems = items.subList(
                                fromIndex = startIndex,
                                toIndex = minOf(startIndex + columns, items.size)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (rowItems.size == columns) {
                                    rowItems.forEach { item ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            itemContent(item, Modifier.fillMaxSize())
                                        }
                                    }
                                } else {
                                    // add spacers either side
                                    val avgMargin = (columns - rowItems.size) / 2.0f

                                    val leftMargin = floor(avgMargin).toInt()
                                    val rightMargin = ceil(avgMargin).toInt()

                                    // Add invisible spacers for incomplete rows to maintain centering
                                    repeat(leftMargin) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }

                                    rowItems.forEach { item ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            itemContent(item, Modifier.fillMaxSize())
                                        }
                                    }

                                    repeat(rightMargin) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A composable that displays a grid of items with a dynamic layout.
 *
 * Layout behavior:
 * - 1 item: fills the entire space
 * - 2+ items: creates a centered grid layout with number of rows determined by sqrt of item count,
 *   maintaining parent's aspect ratio
 *
 * This DynamicGridLayout, the [DynamicRowColumnGridLayout], creates rows containing columns. For certain use cases, like 16:9 video, [DynamicColumnRowGridLayout] may be more appropriate.
 */
@Composable
@Suppress("Unused")
fun <T> DynamicRowColumnGridLayout(
    modifier: Modifier = Modifier,
    items: List<T>,
    itemContent: @Composable (T, Modifier) -> Unit
) {
    Box(modifier = modifier) {
        when {
            items.isEmpty() -> return@Box
            items.size == 1 -> {
                itemContent(items[0], Modifier.fillMaxSize())
            }

            else -> {
                val rows = sqrt(items.size.toFloat()).roundToInt()
                val columns = (items.size + rows - 1) / rows

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        for (column in 0 until columns) {
                            val startIndex = column * rows
                            val columnIndex = items.subList(
                                fromIndex = startIndex,
                                toIndex = minOf(startIndex + rows, items.size)
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (columnIndex.size == rows) {
                                    columnIndex.forEach { item ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            itemContent(item, Modifier.fillMaxSize())
                                        }
                                    }
                                } else {
                                    // add spacers either side
                                    val avgMargin = (rows - columnIndex.size) / 2.0f

                                    val leftMargin = floor(avgMargin).toInt()
                                    val rightMargin = ceil(avgMargin).toInt()

                                    // Add invisible spacers for incomplete columns to maintain centering
                                    repeat(leftMargin) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }

                                    columnIndex.forEach { item ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            itemContent(item, Modifier.fillMaxSize())
                                        }
                                    }

                                    repeat(rightMargin) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun DynamicGridLayoutPreview() {
    DynamicColumnRowGridLayout(
        items = (1..50).toList(),
        modifier = Modifier.fillMaxSize(),
        itemContent = { i, modifier ->
            DynamicColumnRowGridLayout(
                items = (1..i).toList(),
                modifier = modifier
                    .background(Color.DarkGray)
                    .padding(1.dp),
                itemContent = { _, modifier2 ->
                    Box(
                        modifier = modifier2
                            .fillMaxSize()
                            .padding(1.dp)
                            .background(Color.Gray)
                    )
                }
            )
        }
    )
}

@Preview
@Composable
private fun DynamicRowColumnGridLayoutPreview() {
    DynamicRowColumnGridLayout(
        items = (1..50).toList(),
        modifier = Modifier.fillMaxSize(),
        itemContent = { i, modifier ->
            DynamicRowColumnGridLayout(
                items = (1..i).toList(),
                modifier = modifier
                    .background(Color.DarkGray)
                    .padding(1.dp),
                itemContent = { _, modifier2 ->
                    Box(
                        modifier = modifier2
                            .fillMaxSize()
                            .padding(1.dp)
                            .background(Color.Gray)
                    )
                }
            )
        }
    )
}
