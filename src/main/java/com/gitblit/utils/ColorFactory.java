package com.gitblit.utils;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Factory for creating color maps.
 */
public class ColorFactory {

	private static final double MAX_TINT_FACTOR = 1;

	private static final double MIN_TINT_FACTOR = 0.2;

	private static final double FIXED_TINT_FACTOR = 0.85;

	/**
	 * Builds a map of the supplied keys to a random color tinted according to
	 * the key's position in the set.
	 *
	 * Depending on the number of keys in the set a tint is calculated from 1.0
	 * (I.e. white) to a minimum tint. The keys are sorted such that the
	 * "lowest" value will have a full tint applied to it (1.0) with an equally
	 * decreasing tint applied to each key thereafter.
	 *
	 * @param keys The keys to create a tinted color for.
	 * @return The map of key to tinted color.
	 */
	public <T> Map<T, String> getGraduatedColorMap(Set<T> keys) {
		Map<T, String> colorMap = new HashMap<T, String>();

		Color baseColor = getRandomColor();
		double tintStep = (MAX_TINT_FACTOR - MIN_TINT_FACTOR) / keys.size();

		double currentTint = MAX_TINT_FACTOR;
		for (T key : keys) {
			Color color = tintColor(baseColor, currentTint);

			colorMap.put(key, getColorString(color));

			currentTint -= tintStep;
		}

		return colorMap;
	}

	/**
	 * Builds a map of the supplied keys to random colors.
	 *
	 * Each color is selected randomly and tinted with a fixed tint.
	 *
	 * @param keys The keys to create the mapped colors.
	 * @return The map of key to random color.
	 */
	public <T> Map<T, String> getRandomColorMap(Set<T> keys) {
		Map<T, String> colorMap = new HashMap<T, String>();

		for (T key : keys) {
			Color color = tintColor(getRandomColor(), FIXED_TINT_FACTOR);
			colorMap.put(key, getColorString(color));
		}

		return colorMap;
	}

	private Color getRandomColor() {
		Random random = new Random();

		Color randomColor = new Color(random.nextInt(256), random.nextInt(256),
				random.nextInt(256));

		return randomColor;
	}

	private Color tintColor(Color origColor, double tintFactor) {
		int tintedRed = applyTint(origColor.getRed(), tintFactor);
		int tintedGreen = applyTint(origColor.getGreen(), tintFactor);
		int tintedBlue = applyTint(origColor.getBlue(), tintFactor);

		Color tintedColor = new Color(tintedRed, tintedGreen, tintedBlue);

		return tintedColor;
	}

	/**
	 * Convert the color to an HTML compatible color string in hex format E.g.
	 * #FF0000
	 *
	 * @param color The color to convert
	 * @return The string version of the color I.e. #RRGGBB
	 */
	private String getColorString(Color color) {
		return "#" + Integer.toHexString(color.getRGB() & 0x00ffffff);
	}

	/**
	 * Tint the supplied color with a tint factor (0 to 1 inclusive) to make the
	 * colour more pale I.e. closer to white.
	 *
	 * A Tint of 0 has no effect, a Tint of 1 turns the color white.
	 *
	 * @param color The original color
	 * @param tintFactor The factor - 0 to 1 inclusive
	 * @return The tinted color.
	 */
	private int applyTint(int color, double tintFactor) {
		return (int) (color + ((255 - color) * tintFactor));
	}
}
