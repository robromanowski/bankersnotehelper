package com.betterbankersnote;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class BetterBankersNotePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BetterBankersNotePlugin.class);
		RuneLite.main(args);
	}
}