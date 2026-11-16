package com.potionfavourites;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(PotionFavouritesConfig.GROUP)
public interface PotionFavouritesConfig extends Config {
	String GROUP = "potionfavourites";

	String ORDER = "order";

	@ConfigItem(
		keyName = "insert",
		name = "Insert mode",
		description = "Dropping a favourite onto another inserts it at that position and shifts the rest, instead of swapping them",
		position = 0
	)
	default boolean insert() {
		return false;
	}

	@ConfigItem(
		keyName = ORDER,
		name = "Favourite order",
		description = "Potion ids in the order they are shown",
		hidden = true
	)
	default String order() {
		return "";
	}
}
