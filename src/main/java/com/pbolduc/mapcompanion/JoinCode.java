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

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Finding a group code inside whatever the player actually pasted.
 *
 * <p>The code used to be typed into a settings field, six characters at a time, usually from a
 * phone photo of a Discord message — and that is the friction this class removes. What lands on a
 * clipboard is almost never a bare code: it is an invite link, or a whole invite message, or the
 * reader's own <i>RuneLite code</i> from the map's panel, which is the group code with a personal
 * suffix on it ({@code ABC234-K7PQ2M3X}). So the join field takes all of them and digs the code
 * out, rather than making a player work out which part of a sentence is the six characters.
 *
 * <p><b>The suffix is the half that matters most and is easiest to lose.</b> It binds this
 * installation to the browser that minted it, which is how the map stops guessing which dot is the
 * reader. It rides in on the same paste and is stored beside the code until the map says it has
 * been claimed — see {@link Pairing}.
 *
 * <p><b>Why the patterns are tried in an order.</b> A pasted invite message contains the code more
 * than once ("…/map?join=ABC234&task=companion — code ABC234"), and a RuneLite code contains a
 * bare code inside it. Reading the most specific form first is what makes one paste of the whole
 * message land the same code as one paste of the link, and what stops {@code ABC234-K7PQ2M3X}
 * being read as a bare {@code ABC234} with the suffix silently dropped.
 *
 * <p><b>And why two of them are anchored to the whole paste.</b> The last two patterns read the
 * grouped form the map's panel prints, {@code ABC 234} and {@code ABC 234-K7PQ2M3X}, which is what
 * somebody has when they are typing a code off one screen rather than copying it. Two runs of three
 * are a shape ordinary English is full of, so those two only ever match a paste that is nothing
 * else.
 *
 * <p><b>Why a bare six-character token is accepted last, and reluctantly.</b> The alphabet has no
 * {@code O}, {@code I}, {@code L}, {@code U}, {@code 0} or {@code 1}, which rules out most English
 * words, but not all of them — {@code SYSTEM} is six legal characters. Every more specific form is
 * therefore tried first, and the bare form only matches a token standing on its own, so a code
 * inside a longer run of letters and digits is not one.
 */
public final class JoinCode
{
	/**
	 * The map's alphabet, character for character — {@code lib/companion-code.ts}.
	 *
	 * <p>No {@code 0/O}, {@code 1/I/L} or {@code U/V} confusion, because this is read off a screen
	 * and typed into a game client. The suffix uses the same alphabet, which is the whole reason
	 * one pattern can describe both halves of a RuneLite code.
	 */
	static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ";

	static final int CODE_LENGTH = 6;
	static final int SUFFIX_LENGTH = 8;

	/**
	 * The length of a member secret, which is the one thing in this file nobody ever pastes.
	 *
	 * <p>It lives here anyway because this class is where the map's alphabet is written down, and a
	 * second copy of that alphabet is a second thing to keep in step with {@code lib/companion-code.ts}.
	 * Thirty-two characters of it is what the map issues; see {@link #isWellFormedSecret}.
	 */
	static final int SECRET_LENGTH = 32;

	private static final String CH = "[" + ALPHABET + "]";

	/**
	 * A boundary that is not part of a code.
	 *
	 * <p>Letters and digits, rather than {@code \b}: a word boundary would happily find six legal
	 * characters inside {@code K7PQ2M3XABC234}, and a code that arrives glued to something else is
	 * a paste that went wrong rather than a code.
	 */
	private static final String NOT_ADJACENT = "[A-Z0-9]";

	/** {@code ABC234-K7PQ2M3X} — the reader's own code from the map's Connect RuneLite card. */
	private static final Pattern RUNELITE_CODE = Pattern.compile(
			"(?<!" + NOT_ADJACENT + ")(" + CH + "{" + CODE_LENGTH + "})-(" + CH + "{" + SUFFIX_LENGTH + "})"
			+ "(?!" + NOT_ADJACENT + ")");

	/** An invite link, whether pasted alone or inside the message it arrived in. */
	private static final Pattern LINK = Pattern.compile(
											"JOIN=(" + CH + "{" + CODE_LENGTH + "})(?!" + NOT_ADJACENT + ")");

	/** The words this plugin's own invite message uses: "…— code ABC234". */
	private static final Pattern LABELLED = Pattern.compile(
			"CODE\\s*[:=]?\\s*(" + CH + "{" + CODE_LENGTH + "})(?!" + NOT_ADJACENT + ")");

	/** Six characters standing on their own. */
	private static final Pattern BARE = Pattern.compile(
											"(?<!" + NOT_ADJACENT + ")(" + CH + "{" + CODE_LENGTH + "})(?!" + NOT_ADJACENT + ")");

	/**
	 * {@code ABC 234} — the map's panel prints a code in two halves because six characters read
	 * and type more easily that way, so that is what a reader copying it off the screen has.
	 *
	 * <p>Anchored to the whole paste, unlike every pattern above it, and that is not fussiness:
	 * three legal characters, a space and three more is a shape ordinary English hits all the time
	 * — "THE MAP" is six legal characters — and a sentence must not be mistaken for a code. A
	 * reader who copied the two halves off the panel has copied exactly those two halves.
	 */
	private static final Pattern GROUPED = Pattern.compile(
			"^(" + CH + "{3})[ -](" + CH + "{3})$");

	/**
	 * {@code ABC 234-K7PQ2M3X} — the same two halves with a suffix after them.
	 *
	 * <p>Nothing the map puts on a clipboard looks like this; it is what somebody types when they
	 * are reading a RuneLite code off one screen and typing it into another, because the panel
	 * prints the group code grouped and the eye copies what it sees. Without this the suffix and
	 * the code went together: the halves are not six characters in a row, so nothing above matched,
	 * and {@link #GROUPED} ends at the second half — so the whole paste was answered "that isn't a
	 * group code" while plainly containing one.
	 *
	 * <p>Anchored to the whole paste, exactly like {@link #GROUPED} and with the same trade made
	 * knowingly. The shape is rarer with a suffix on it but not rare: upper-cased, "the
	 * map-starters" is three legal characters, a space, three more, a dash and eight more. What the
	 * anchor buys is that such a phrase inside a sentence is not a code — an unanchored pattern
	 * would find one in any message that happened to contain the words. What it does not buy is the
	 * phrase pasted on its own, which still reads as a code, as "THE MAP" has always read as one.
	 * That is survivable for the reason it already was: this is a field labelled <i>Join a group</i>,
	 * and <i>Paste</i> prints the code it found and waits rather than joining.
	 *
	 * <p>The price of the anchor is that a grouped code inside a sentence is not found at all.
	 * Nobody's clipboard holds this form — the map's Copy button gives the ungrouped one, which
	 * every pattern above reads out of any amount of surrounding text — so the reader this is for is
	 * somebody typing what they can see, and what they type is the code and nothing else.
	 */
	private static final Pattern GROUPED_WITH_SUFFIX = Pattern.compile(
			"^(" + CH + "{3})[ -](" + CH + "{3})-(" + CH + "{" + SUFFIX_LENGTH + "})$");

	/** Six characters, upper case. What goes in {@code pairingCode} and on every request. */
	public final String code;

	/**
	 * Eight characters, or null when the paste carried no suffix.
	 *
	 * <p>Null is an ordinary outcome, not a failure: a bare code still joins, and the map answers
	 * a member it does not recognise by asking for the RuneLite code — which is a sentence a player
	 * can act on.
	 */
	@Nullable
	public final String suffix;

	private JoinCode(String code, @Nullable String suffix)
	{
		this.code = code;
		this.suffix = suffix;
	}

	/**
	 * Read a code out of arbitrary pasted text, or return null.
	 *
	 * <p>Null means "nothing in here is a code", and the panel answers it with one sentence. It
	 * does not mean the text was malformed in some interesting way: a RuneLite code whose suffix
	 * did not survive the copy still yields its six-character code, because refusing text that
	 * visibly contains a code helps nobody.
	 */
	@Nullable
	public static JoinCode parse(@Nullable String pasted)
	{
		if (pasted == null)
		{
			return null;
		}
		/** Upper case once, here: a code read aloud, retyped or lower-cased is the same code. */
		String text = pasted.toUpperCase(Locale.ROOT);

		Matcher full = RUNELITE_CODE.matcher(text);
		if (full.find())
		{
			return new JoinCode(full.group(1), full.group(2));
		}
		for (Pattern pattern : new Pattern[] {LINK, LABELLED, BARE})
		{
			Matcher matcher = pattern.matcher(text);
			if (matcher.find())
			{
				return new JoinCode(matcher.group(1), null);
			}
		}
		/** Most specific first here too: the suffixed form ends where the bare one does not. */
		String trimmed = text.trim();
		Matcher groupedWithSuffix = GROUPED_WITH_SUFFIX.matcher(trimmed);
		if (groupedWithSuffix.find())
		{
			return new JoinCode(
					   groupedWithSuffix.group(1) + groupedWithSuffix.group(2), groupedWithSuffix.group(3));
		}
		Matcher grouped = GROUPED.matcher(trimmed);
		if (grouped.find())
		{
			return new JoinCode(grouped.group(1) + grouped.group(2), null);
		}
		return null;
	}

	/** True for exactly six characters of the map's alphabet. */
	public static boolean isWellFormedCode(@Nullable String value)
	{
		return isWellFormed(value, CODE_LENGTH);
	}

	/**
	 * True for exactly eight characters of the same alphabet.
	 *
	 * <p>Checked rather than assumed because the suffix is stored in a settings field a player can
	 * type into, and an unsendable header is better than a malformed one: a wrong suffix is ignored
	 * by the map, but a header this plugin knows is nonsense should not be sent at all.
	 */
	public static boolean isWellFormedSuffix(@Nullable String value)
	{
		return isWellFormed(value, SUFFIX_LENGTH);
	}

	/**
	 * True for exactly thirty-two characters of the same alphabet.
	 *
	 * <p>Checked in both directions, and each direction is a different worry. On the way in, a
	 * reply that carries something other than a secret is a map this build does not understand, and
	 * storing whatever arrived would put a header on every later request that can only ever be
	 * refused. On the way out, this comes from a config key, and config keys can be edited.
	 */
	public static boolean isWellFormedSecret(@Nullable String value)
	{
		return isWellFormed(value, SECRET_LENGTH);
	}

	private static boolean isWellFormed(@Nullable String value, int length)
	{
		if (value == null || value.length() != length)
		{
			return false;
		}
		for (int i = 0; i < value.length(); i++)
		{
			if (ALPHABET.indexOf(value.charAt(i)) < 0)
			{
				return false;
			}
		}
		return true;
	}

	/** {@code ABC234} as {@code ABC 234}, the way the map's panel prints it. For labels only. */
	public static String forDisplay(String code)
	{
		return code.length() == CODE_LENGTH ? code.substring(0, 3) + " " + code.substring(3) : code;
	}
}
