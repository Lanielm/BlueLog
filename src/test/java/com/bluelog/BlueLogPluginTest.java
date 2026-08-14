package com.bluelog;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Launches a RuneLite client with this plugin loaded, for manual testing from the IDE.
 */
public class BlueLogPluginTest
{
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BlueLogPlugin.class);
		RuneLite.main(args);
	}
}
