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

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The lifecycle of a RuneLite code, the secrets the map issues, and the list of groups this
 * installation remembers.
 *
 * <p>All three are the kind of thing that goes wrong without a symptom. A suffix that is sent after
 * it has been claimed is a secret kept for no reason; a suffix that survives a change of code is one
 * group's credential offered to another; a remembered list that keeps a group somebody left is a
 * one-press way back into a group they meant to leave. None of those makes anything visibly fail,
 * which is why they are pinned here rather than trusted.
 *
 * <p>The member secret fails quietly in both directions, which is why its rules are here too. Filed
 * under the wrong group it is sent to a map that never issued it, on every request; thrown away
 * when a group changes it cannot be asked for again, because the map issues one once and then never
 * mentions it — so the group you switch back to is one this plugin can post to and never hear from.
 * The wire side of the same feature is {@code MemberSecretTest}.
 *
 * <p>Against a map rather than RuneLite's {@code ConfigManager}, through the two-line
 * {@link ConfigStore} seam — see that class for why the seam exists.
 */
public class PairingTest
{
	/** The member every secret here is filed for; a member is one character. */
	private static final String MEMBER = "member-1";

	/** Thirty-two characters of the map's alphabet, which is the shape the map issues. */
	private static final String SECRET = "K7PQ2M3XABCDEFGHJKMNPQRSTVWXYZ23";

	private final Gson gson = new Gson();

	@Test
	public void joiningWithARuneLiteCodeStoresBothHalves()
	{
		FakeStore store = new FakeStore();
		Pairing pairing = new Pairing(store, gson);

		pairing.join(JoinCode.parse("ABC234-K7PQ2M3X"));

		assertEquals("ABC234", pairing.code());
		assertEquals("K7PQ2M3X", pairing.suffix());
	}

	@Test
	public void theSuffixIsSentUntilTheMapClaimsIt()
	{
		FakeStore store = new FakeStore();
		Pairing pairing = new Pairing(store, gson);
		pairing.join(JoinCode.parse("ABC234-K7PQ2M3X"));

		/** Two posts before the reply that claims it: the same header goes on both. */
		assertEquals("K7PQ2M3X", pairing.suffix());
		assertEquals("K7PQ2M3X", pairing.suffix());

		pairing.clearSuffix();

		assertNull(pairing.suffix());
		/** And the group is untouched: claiming a code is not leaving one. */
		assertEquals("ABC234", pairing.code());
	}

	@Test
	public void changingTheCodeThrowsTheSuffixAway()
	{
		FakeStore store = new FakeStore();
		Pairing pairing = new Pairing(store, gson);
		pairing.join(JoinCode.parse("ABC234-K7PQ2M3X"));

		pairing.join(JoinCode.parse("DEF567"));

		assertEquals("DEF567", pairing.code());
		assertNull("a suffix minted for one group cannot mean anything in another", pairing.suffix());
	}

	@Test
	public void switchingToARememberedGroupDropsTheSuffixToo()
	{
		FakeStore store = new FakeStore();
		Pairing pairing = new Pairing(store, gson);
		pairing.join(JoinCode.parse("ABC234"));
		pairing.join(JoinCode.parse("DEF567-K7PQ2M3X"));

		pairing.switchTo("ABC234");

		assertEquals("ABC234", pairing.code());
		assertNull(pairing.suffix());
	}

	@Test
	public void refusesToSendASuffixThatIsNotOne()
	{
		FakeStore store = new FakeStore();
		Pairing pairing = new Pairing(store, gson);
		pairing.join(JoinCode.parse("ABC234"));

		/** A settings field somebody can type into, so what comes out of it is checked. */
		store.set(MapCompanionConfig.KEY_PAIRING_SUFFIX, "not a suffix");
		assertNull(pairing.suffix());

		store.set(MapCompanionConfig.KEY_PAIRING_SUFFIX, "K7PQ2M3");
		assertNull(pairing.suffix());

		/** Case and stray spaces are the player's, not the map's. */
		store.set(MapCompanionConfig.KEY_PAIRING_SUFFIX, " k7pq2m3x ");
		assertEquals("K7PQ2M3X", pairing.suffix());
	}

	@Test
	public void aSecretIsFiledUnderTheGroupThatIssuedIt()
	{
		FakeStore store = new FakeStore();
		Pairing pairing = new Pairing(store, gson);
		pairing.join(JoinCode.parse("ABC234"));

		pairing.rememberSecret("ABC234", MEMBER, SECRET);

		assertEquals(SECRET, pairing.secret(MEMBER));

		/** Another group is another member row on the map, and nothing of this one's goes there. */
		pairing.join(JoinCode.parse("DEF567"));
		assertNull(pairing.secret(MEMBER));

		/** And coming back finds it, which is the whole reason it is filed rather than replaced. */
		pairing.switchTo("ABC234");
		assertEquals(SECRET, pairing.secret(MEMBER));
	}

	@Test
	public void anAltIsNeverHandedItsMainsSecret()
	{
		FakeStore store = new FakeStore();
		Pairing pairing = new Pairing(store, gson);
		pairing.join(JoinCode.parse("ABC234"));
		pairing.rememberSecret("ABC234", MEMBER, SECRET);

		/** The map issues one per member and a member is one character: the alt waits for its own. */
		assertNull(pairing.secret("member-2"));
		assertNull("and a character nobody is logged in as presents nothing", pairing.secret(null));
		assertEquals(SECRET, pairing.secret(MEMBER));
	}

	@Test
	public void refusesToKeepOrToSendSomethingThatIsNotASecret()
	{
		FakeStore store = new FakeStore();
		Pairing pairing = new Pairing(store, gson);
		pairing.join(JoinCode.parse("ABC234"));

		/** Too short, and the wrong alphabet: a reply this build cannot read leaves nothing behind. */
		pairing.rememberSecret("ABC234", MEMBER, "K7PQ2M3X");
		pairing.rememberSecret("ABC234", MEMBER, "k7pq2m3xabcdefghjkmnpqrstvwxyz23");
		assertNull(pairing.secret(MEMBER));

		pairing.rememberSecret("ABC234", MEMBER, SECRET);
		assertEquals(SECRET, pairing.secret(MEMBER));

		/** And it is a config key, so it can be edited into something that is not one. */
		store.set(Pairing.KEY_MEMBER_SECRETS, "{\"ABC234\":\"nope\"}");
		assertNull(pairing.secret(MEMBER));

		store.set(Pairing.KEY_MEMBER_SECRETS, "{ this is not json");
		assertNull(pairing.secret(MEMBER));
	}

	@Test
	public void aCodeTypedInLowerCaseStillFindsItsOwnSecret()
	{
		/**
		 * The settings field takes what somebody types, and every list here upper-cases what it
		 * holds. A secret written under {@code ABC234} and looked for under {@code abc234} is a
		 * credential that is on disk, valid, and never sent — a stream refused for no visible reason.
		 */
		FakeStore store = new FakeStore();
		Pairing pairing = new Pairing(store, gson);
		store.set(MapCompanionConfig.KEY_PAIRING_CODE, "abc234");

		pairing.rememberSecret("abc234", MEMBER, SECRET);

		assertEquals(SECRET, pairing.secret(MEMBER));
	}

	@Test
	public void aGroupThatFallsOffTheRememberedListTakesItsSecretWithIt()
	{
		/**
		 * The list is already the answer to "which groups is this installation still in", so it is
		 * what bounds the secrets as well. Without that the object only grows, one entry per group
		 * ever joined, in a config file nothing else prunes.
		 */
		FakeStore store = new FakeStore();
		Pairing pairing = new Pairing(store, gson);
		pairing.join(JoinCode.parse("AAA222"));
		pairing.rememberSecret("AAA222", MEMBER, SECRET);

		String[] rest =
		{
			"BBB333", "CCC444", "DDD555", "EEE666", "FFF777", "GGG888", "HHH999", "JJJ222",
		};
		for (String code : rest)
		{
			pairing.join(JoinCode.parse(code));
			pairing.rememberSecret(code, MEMBER, SECRET);
		}

		assertFalse(pairing.knownCodes().contains("AAA222"));
		pairing.switchTo("AAA222");
		assertNull("a group nobody can press a button to reach keeps no credential", pairing.secret(MEMBER));
	}

	@Test
	public void leavingForgetsEverySecretRatherThanOnlyThisGroupsOne()
	{
		/**
		 * The panel forgets the member id in the same breath, and a secret says which member of a
		 * group this installation is — so once the id is gone there is nothing for any of them to
		 * prove. See {@link MapCompanionPanel#leaveGroup}.
		 */
		FakeStore store = new FakeStore();
		Pairing pairing = new Pairing(store, gson);
		pairing.join(JoinCode.parse("ABC234"));
		pairing.rememberSecret("ABC234", MEMBER, SECRET);
		pairing.join(JoinCode.parse("DEF567"));
		pairing.rememberSecret("DEF567", MEMBER, SECRET);

		pairing.leave();

		pairing.switchTo("ABC234");
		assertNull(pairing.secret(MEMBER));
	}

	@Test
	public void remembersGroupsMostRecentFirst()
	{
		FakeStore store = new FakeStore();
		Pairing pairing = new Pairing(store, gson);

		pairing.join(JoinCode.parse("ABC234"));
		pairing.join(JoinCode.parse("DEF567"));
		pairing.join(JoinCode.parse("GHJ789"));

		assertEquals(Arrays.asList("GHJ789", "DEF567", "ABC234"), pairing.knownCodes());
	}

	@Test
	public void rejoiningAGroupMovesItBackToTheTopRatherThanListingItTwice()
	{
		FakeStore store = new FakeStore();
		Pairing pairing = new Pairing(store, gson);
		pairing.join(JoinCode.parse("ABC234"));
		pairing.join(JoinCode.parse("DEF567"));

		pairing.join(JoinCode.parse("ABC234"));

		assertEquals(Arrays.asList("ABC234", "DEF567"), pairing.knownCodes());
	}

	@Test
	public void keepsAtMostEight()
	{
		FakeStore store = new FakeStore();
		Pairing pairing = new Pairing(store, gson);
		String[] codes =
		{
			"AAA222", "BBB333", "CCC444", "DDD555", "EEE666", "FFF777", "GGG888", "HHH999", "JJJ222",
		};
		for (String code : codes)
		{
			pairing.join(JoinCode.parse(code));
		}

		assertEquals(Pairing.MAX_KNOWN_CODES, pairing.knownCodes().size());
		assertEquals("JJJ222", pairing.knownCodes().get(0));
		/** The one that fell off is the one nobody has been back to. */
		assertFalse(pairing.knownCodes().contains("AAA222"));
	}

	@Test
	public void leavingForgetsTheGroupAsWellAsTheCode()
	{
		FakeStore store = new FakeStore();
		Pairing pairing = new Pairing(store, gson);
		pairing.join(JoinCode.parse("ABC234"));
		pairing.join(JoinCode.parse("DEF567-K7PQ2M3X"));

		pairing.leave();

		assertEquals("", pairing.code());
		assertNull(pairing.suffix());
		assertEquals(Arrays.asList("ABC234"), pairing.knownCodes());
	}

	@Test
	public void survivesAListThatIsNotAList()
	{
		FakeStore store = new FakeStore();
		Pairing pairing = new Pairing(store, gson);

		store.set(Pairing.KEY_KNOWN_CODES, "{ this is not json");
		assertTrue(pairing.knownCodes().isEmpty());

		/** Nor is a code that is not one, however it got in there. */
		store.set(Pairing.KEY_KNOWN_CODES, "[\"ABC234\", \"nope\", null, \"ABC234\"]");
		assertEquals(Arrays.asList("ABC234"), pairing.knownCodes());
	}

	@Test
	public void keepsTheMapsNameForAGroupAndWritesItOnlyWhenItChanges()
	{
		/**
		 * The map sends a member's group name on every reply, every couple of seconds, and a config
		 * write raises a {@code ConfigChanged} that runs this plugin's own handler. So the same
		 * name again — however it is spaced, whatever case the code was typed in — is no write.
		 */
		FakeStore store = new FakeStore();
		Pairing pairing = new Pairing(store, gson);
		pairing.join(JoinCode.parse("ABC234"));
		pairing.rememberName("ABC234", "Sunday raids");
		int writes = store.writes;

		pairing.rememberName("ABC234", "Sunday raids");
		pairing.rememberName("abc234", "  Sunday   raids ");
		assertEquals(writes, store.writes);
		assertEquals("Sunday raids", pairing.name("ABC234"));

		pairing.rememberName("ABC234", "Tuesday raids");
		assertEquals("a rename is one write", writes + 1, store.writes);
		assertEquals("Tuesday raids", pairing.name("ABC234"));
	}

	@Test
	public void keepsOnlyWhatTheMapCouldHaveSentAsAName()
	{
		FakeStore store = new FakeStore();
		Pairing pairing = new Pairing(store, gson);
		pairing.join(JoinCode.parse("ABC234"));
		pairing.rememberName("ABC234", "Sunday raids");

		/** Nothing, blank, or longer than the map allows: the held name stays. */
		pairing.rememberName("ABC234", null);
		pairing.rememberName("ABC234", "   ");
		pairing.rememberName("ABC234", "Twenty-five characters!!!");
		assertEquals("Sunday raids", pairing.name("ABC234"));

		/** Flattened the way the map's own normaliser flattens: no line breaks into a menu row. */
		pairing.rememberName("ABC234", "Clan\thunt\n");
		assertEquals("Clan hunt", pairing.name("ABC234"));

		/** And it is a config key, so it can be edited into something that is not a list of names. */
		store.set(Pairing.KEY_GROUP_NAMES, "{ this is not json");
		assertNull(pairing.name("ABC234"));
	}

	@Test
	public void forgettingOrLeavingAGroupForgetsItsName()
	{
		FakeStore store = new FakeStore();
		Pairing pairing = new Pairing(store, gson);
		pairing.join(JoinCode.parse("ABC234"));
		pairing.rememberName("ABC234", "Sunday raids");
		pairing.join(JoinCode.parse("DEF567"));
		pairing.rememberName("DEF567", "Clan hunt");

		pairing.forget("ABC234");
		assertNull(pairing.name("ABC234"));
		assertEquals("the rest of the list keeps its names", "Clan hunt", pairing.name("DEF567"));

		pairing.leave();
		assertNull(pairing.name("DEF567"));
	}

	@Test
	public void aGroupThatFallsOffTheRememberedListTakesItsNameWithIt()
	{
		/** The list bounds the names as it bounds the secrets, so the object cannot only grow. */
		FakeStore store = new FakeStore();
		Pairing pairing = new Pairing(store, gson);
		pairing.join(JoinCode.parse("AAA222"));
		pairing.rememberName("AAA222", "First group");

		String[] rest =
		{
			"BBB333", "CCC444", "DDD555", "EEE666", "FFF777", "GGG888", "HHH999", "JJJ222",
		};
		for (String code : rest)
		{
			pairing.join(JoinCode.parse(code));
			pairing.rememberName(code, "Group " + code);
		}

		assertEquals(Pairing.MAX_KNOWN_CODES, pairing.knownCodes().size());
		assertNull(pairing.name("AAA222"));
		assertEquals("Group JJJ222", pairing.name("JJJ222"));
	}

	/** The two lines of key-value store {@link Pairing} actually uses, in a map. */
	private static final class FakeStore implements ConfigStore
	{
		private final Map<String, String> values = new HashMap<>();
		/** How many writes there have been, for the rules that are about not writing. */
		private int writes = 0;

		@Nullable
		@Override
		public String get(String key)
		{
			return values.get(key);
		}

		@Override
		public void set(String key, String value)
		{
			writes++;
			values.put(key, value);
		}

		@Override
		public void unset(String key)
		{
			values.remove(key);
		}
	}
}
