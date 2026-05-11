package com.smartamenities.app.ui.amenities

enum class AmenityTypeOption(
    val apiValue: String,
    val displayLabel: String
) {
    MEN_RESTROOM("MEN_RESTROOM", "Men's Restroom"),
    WOMEN_RESTROOM("WOMEN_RESTROOM", "Women's Restroom"),
    ACCESSIBLE_RESTROOM("ACCESSIBLE_RESTROOM", "Accessible Restroom");

    companion object {
        fun fromApiValue(value: String?): AmenityTypeOption? {
            return entries.firstOrNull { it.apiValue.equals(value, ignoreCase = true) }
        }
    }
}


