/*
 * Copyright (c) 2026, pbolduc
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.pbolduc.mapcompanion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import net.runelite.client.plugins.PluginDescriptor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The Plugin Hub lists the plugin by the manifest's description and tags, and RuneLite's own plugin
 * list by the {@code @PluginDescriptor}'s, so a player meets one or the other first. The release
 * checklist asks that they agree and still say what the plugin does; they had drifted into two
 * different sentences, both describing a position tracker with a pairing invites replaced.
 */
public class PluginDescriptionTest
{
	@Test
	public void theHubAndRuneLitesPluginListDescribeThePluginAlike() throws IOException
	{
		Properties manifest = new Properties();
		try (InputStream in = Files.newInputStream(Paths.get("runelite-plugin.properties")))
		{
			manifest.load(in);
		}
		PluginDescriptor descriptor = MapCompanionPlugin.class.getAnnotation(PluginDescriptor.class);
		assertEquals(manifest.getProperty("description"), descriptor.description());
		assertEquals(manifest.getProperty("tags"), String.join(",", descriptor.tags()));
		for (String feature : new String[]{"routes", "search", "farm runs", "shooting stars", "osrs-map.com"})
		{
			assertTrue("says " + feature, descriptor.description().contains(feature));
		}
		assertFalse("no pairing by a shared code", descriptor.description().contains("code"));
	}
}
