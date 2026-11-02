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

import com.google.inject.Singleton;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * One character's local counter and the server's ranked places. Counts never invalidate the list.
 * The relay epoch and revision order both receive channels; a character/task/preferences change
 * hides the previous answer immediately, before a request or its replacement answer can finish.
 *
 * <p>A new ranking choice for the same task keeps the places it hid as {@link View#previous}, so the
 * card can show them greyed under the controls just used until the new ones arrive. That copy is
 * for the eye only: {@link View#options} stays empty meanwhile, so nothing acts on places ranked
 * for another choice.
 */
@Singleton
final class SlayerState
{
	private final DirectiveEpoch epoch = new DirectiveEpoch();
	private String member;
	private String task;
	private String area;
	private String order = "best";
	private boolean hideWilderness = true;
	private Integer remaining;
	private long revision;
	private long seenRevision;
	private RelayResponse.SlayerOptions options;
	/** The places the last choice change hid, for the same task; display only. */
	private RelayResponse.SlayerOptions previous;
	private long receivedAt;

	static final class View
	{
		final String task;
		final String area;
		final Integer remaining;
		final RelayResponse.SlayerOptions options;
		final boolean stale;
		/** Display only: the places received before the choice changed, while {@link #options} waits. */
		final RelayResponse.SlayerOptions previous;
		/** The choice the places are ranked, or being ranked, for. */
		final String order;
		final boolean hideWilderness;

		View(String task, String area, Integer remaining, RelayResponse.SlayerOptions options, boolean stale,
			RelayResponse.SlayerOptions previous, String order, boolean hideWilderness)
		{
			this.task = task;
			this.area = area;
			this.remaining = remaining;
			this.options = options;
			this.stale = stale;
			this.previous = previous;
			this.order = order;
			this.hideWilderness = hideWilderness;
		}
	}

	synchronized void local(@Nullable String incomingMember, @Nullable CompanionSnapshot.Slayer local, String incomingOrder, boolean hide)
	{
		if (!Objects.equals(member, incomingMember))
		{
			clear();
			member = incomingMember;
		}
		String nextTask = local == null ? null : local.taskName;
		String nextArea = local == null ? null : local.areaName;
		if (!Objects.equals(task, nextTask) || !Objects.equals(area, nextArea))
		{
			// Another task's places are never shown, greyed or not.
			previous = null;
			options = null;
			seenRevision = 0;
		}
		else if (!Objects.equals(order, incomingOrder) || hideWilderness != hide)
		{
			rankAgain();
		}
		task = nextTask;
		area = nextArea;
		remaining = local == null ? null : local.remaining;
		order = "single".equals(incomingOrder) ? "single" : "best";
		hideWilderness = hide;
	}

	synchronized void choices(String incomingOrder, boolean hide)
	{
		if (!Objects.equals(order, incomingOrder) || hideWilderness != hide)
		{
			rankAgain();
		}
		order = "single".equals(incomingOrder) ? "single" : "best";
		hideWilderness = hide;
	}

	/** A new choice for the same task: its answer is asked for again, and the old places kept to show. */
	private void rankAgain()
	{
		if (options != null)
		{
			previous = options;
		}
		options = null;
		seenRevision = 0;
	}

	synchronized CompanionSnapshot.SlayerPreferences preferences()
	{
		CompanionSnapshot.SlayerPreferences preferences = new CompanionSnapshot.SlayerPreferences();
		preferences.order = order;
		preferences.hideWilderness = hideWilderness;
		preferences.seenRevision = seenRevision;
		return preferences;
	}

	synchronized void accept(@Nullable String incomingMember, @Nullable RelayResponse.Directive directive, long now)
	{
		if (member == null || !member.equals(incomingMember) || directive == null)
		{
			return;
		}
		DirectiveEpoch.Seen scope = epoch.accept(directive.relayEpoch);
		if (scope == DirectiveEpoch.Seen.INVALID)
		{
			return;
		}
		if (scope == DirectiveEpoch.Seen.NEW)
		{
			options = null;
			revision = 0;
			seenRevision = 0;
		}
		receivedAt = now;
		if (directive.slayerRevision <= 0 || directive.slayerRevision < revision)
		{
			return;
		}
		RelayResponse.SlayerOptions incoming = directive.slayerOptions;
		if (incoming != null && (!Objects.equals(task, incoming.taskName) || !Objects.equals(area, incoming.areaName)
								 || !Objects.equals(order, incoming.order) || hideWilderness != incoming.hideWilderness))
		{
			return;
		}
		// An absent field without a revision means "unchanged"; a newer removal means "no task".
		if (incoming == null && task != null)
		{
			return;
		}
		options = incoming;
		previous = null;
		revision = directive.slayerRevision;
		seenRevision = revision;
		receivedAt = now;
	}

	synchronized View view(long now)
	{
		return new View(task, area, remaining, options, receivedAt > 0 && now - receivedAt > 120_000,
			previous, order, hideWilderness);
	}
	synchronized void clear()
	{
		member = null;
		task = null;
		area = null;
		remaining = null;
		options = null;
		previous = null;
		revision = 0;
		seenRevision = 0;
		receivedAt = 0;
		epoch.clear();
	}
}
