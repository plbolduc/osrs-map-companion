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

import javax.annotation.Nullable;

/**
 * The one chat line an update says, once per version, on the first login after it.
 *
 * <p>RuneLite shows no changelog when a plugin updates, so a player learns that the sidebar
 * changed only by finding it changed. One line, as Quest Helper does, says what is new and where
 * the rest is written down: the map's What's new page, which the sidebar links to as well. It is a
 * local game message, drawn by this client and sent nowhere.
 *
 * <p>Only a player who used the plugin before hears it. A fresh install has nothing that changed,
 * so it records the version without a word; telling a new player "updated" would be news about a
 * plugin they have not seen yet.
 */
final class UpdateNotice
{
	/** The last version whose line was said, or recorded without one. */
	static final String KEY_TOLD = "updateNoticeVersion";

	/** What this version changed, in a sentence a player reads between kills. */
	static final String LINE = "a new sidebar: Home, Group and Stars, settings behind the gear, and the game's own "
		+ "pictures in search, friends' news and Stars.";

	/** Where the rest of it is written, as words: a game message cannot carry a link. */
	static final String WHERE = "What's new: osrs-map.com/whats-new";

	private UpdateNotice()
	{
	}

	/**
	 * The line to say now, or null, and the version recorded as told either way.
	 *
	 * @param usedBefore whether this installation was ever connected to a group, the one sign that
	 *     the player has seen an earlier version
	 */
	@Nullable
	static String due(ConfigStore store, boolean usedBefore)
	{
		String told = store.get(KEY_TOLD);
		if (CompanionVersion.VERSION.equals(told))
		{
			return null;
		}
		store.set(KEY_TOLD, CompanionVersion.VERSION);
		if (told == null && !usedBefore)
		{
			return null;
		}
		return LINE;
	}
}
