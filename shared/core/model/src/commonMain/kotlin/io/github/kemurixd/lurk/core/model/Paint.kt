package io.github.kemurixd.lurk.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class PaintFunction { LINEAR_GRADIENT, RADIAL_GRADIENT, URL }

@Serializable
data class PaintStop(
    val at: Float,
    val argb: Int,
)

@Serializable
data class PaintShadow(
    val xOffset: Float,
    val yOffset: Float,
    val radius: Float,
    val argb: Int,
)

@Serializable
data class Paint(
    val id: String,
    val name: String,
    val function: PaintFunction,
    val angle: Int = 0,
    val repeat: Boolean = false,
    val stops: List<PaintStop> = emptyList(),
    val imageUrl: String? = null,
    val shadows: List<PaintShadow> = emptyList(),
)
