// File: /src/main/java/com/bytefish/bytecore/util/StringUtils.java
package com.bytefish.bytecore.util;

import org.bukkit.Material;

public class StringUtils {

	public static String formatItemName(Material material, int amount) {
		// Calculate maximum available space for the name
		// Format: "99x" + name = 15 chars total
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
				// If we can't fit the whole word, try to fit part of it
				int remainingSpace = maxNameLength - result.length();
				if (result.length() == 0 && remainingSpace > 0) {
					// If this is the first word and we have space, take what we can
					return word.substring(
						0,
						Math.min(word.length(), remainingSpace)
					);
				} else if (remainingSpace >= 4) { // Only add partial word if we can show at least 4 chars
					if (result.length() > 0) result.append(" ");
					result
						.append(word.substring(0, remainingSpace - 1))
						.append(".");
				}
				break;
			}
		}

		return result.toString();
	}
}
