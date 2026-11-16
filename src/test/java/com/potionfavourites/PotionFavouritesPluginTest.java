package com.potionfavourites;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PotionFavouritesPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(PotionFavouritesPlugin.class);
		RuneLite.main(args);
	}
}
