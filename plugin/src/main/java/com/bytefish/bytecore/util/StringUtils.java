package com.bytefish.bytecore.util;

import org.bukkit.Material;

public class StringUtils {

	/**
	 * Formats a material name into a readable string with proper capitalization
	 * Handles space constraints for display
	 * @param material The material to format
	 * @param amount The amount of the item (for space calculation)
	 * @return Formatted string
	 */
	public static String formatItemName(Material material, int amount) {
		// Calculate maximum available space
		int amountSpace = String.valueOf(amount).length() + 1; // +1 for 'x'
		int maxNameLength = 15 - amountSpace;

		// Get the material name and format it
		String[] words = material
			.name()
			.toLowerCase()
			.replace('_', ' ')
			.split(" ");
		StringBuilder result = new StringBuilder();

		// Try to fit as many words as possible
		for (int i = 0; i < words.length; i++) {
			String word = words[i];
			// Capitalize first letter
			word = word.substring(0, 1).toUpperCase() + word.substring(1);

			// If this is not the first word, we need space for the space character
			int spaceNeeded = result.length() > 0
				? word.length() + 1
				: word.length();

			// If we can fit the whole word
			if (result.length() + spaceNeeded <= maxNameLength) {
				if (result.length() > 0) {
					result.append(" ");
				}
				result.append(word);
			} else {
				// If this is the first word and we can't fit it all
				if (result.length() == 0 && maxNameLength > 3) {
					return word.substring(0, maxNameLength - 1) + ".";
				}
				// Otherwise just stop here
				break;
			}
		}

		return result.toString();
	}
}
