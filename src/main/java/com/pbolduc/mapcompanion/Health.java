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

import java.awt.Color;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.ui.ColorScheme;

/**
 * What the dot in the sidebar is coloured by.
 *
 * <p>Carried alongside the status sentence rather than parsed back out of it. The sentence is
 * written for a person to read and will be reworded eventually; a colour recovered by matching its
 * text would go quietly wrong the first time that happened, and the wrong answer here is a green
 * dot on a dead connection — the one reading nobody thinks to double-check.
 */
@Getter
@RequiredArgsConstructor
enum Health
{
	/** Nothing is meant to be happening: no pairing code, or the plugin is off. */
	IDLE(ColorScheme.LIGHT_GRAY_COLOR),
	/** Something is meant to be happening and has not yet: connecting, or waiting for a login. */
	WAITING(ColorScheme.PROGRESS_INPROGRESS_COLOR),
	/** A post has succeeded, so the map is being fed. */
	LIVE(ColorScheme.PROGRESS_COMPLETE_COLOR),
	/**
	 * Was live; the last post did not land. Retrying.
	 *
	 * <p>Same colour as {@link #WAITING} on purpose: amber means "not green", and the sentence
	 * beside the dot is what distinguishes "have not connected yet" from "was connected and
	 * the last post missed". A third colour for that would be a legend nobody reads.
	 */
	DEGRADED(ColorScheme.PROGRESS_INPROGRESS_COLOR),
	/** The relay refused in a way retrying will not fix. */
	ERROR(ColorScheme.PROGRESS_ERROR_COLOR);

	private final Color color;
}
