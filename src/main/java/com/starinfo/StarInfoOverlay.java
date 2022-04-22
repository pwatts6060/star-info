/*
 * Copyright (c) 2022, Cute Rock
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.starinfo;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.OverlayUtil;

public class StarInfoOverlay extends Overlay
{

	private final StarInfoPlugin plugin;
	private final StarInfoConfig config;
	private Color textColor;
	private Star lastStar = null;
	private String lastHealthText = "";

	@Inject
	StarInfoOverlay(StarInfoPlugin plugin, StarInfoConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(OverlayPriority.HIGHEST);
	}

	public void updateConfig()
	{
		textColor = config.getTextColor();
	}

	@Override
	public Dimension render(final Graphics2D graphics)
	{
		if (plugin.stars.isEmpty())
		{
			lastStar = null;
			return null;
		}
		Star star = plugin.stars.get(0);
		String text = "T" + star.getTier() + " " + star.getMiners() + "M";
		if (star.getNpc() != null)
		{
			if (star.getNpc().getHealthRatio() >= 0)
			{
				String healthText = " " + (100 * star.getNpc().getHealthRatio() / star.getNpc().getHealthScale()) + "%";
				text += healthText;
				lastHealthText = healthText;
				lastStar = star;
			}
			else if (star.equals(lastStar) && !lastHealthText.isEmpty())
			{
				text += lastHealthText;
			}
		}
		Point starLocation = star.getObject().getCanvasTextLocation(graphics, text, 0);

		if (starLocation != null)
		{
			try
			{
				OverlayUtil.renderTextLocation(graphics, star.getObject().getCanvasTextLocation(graphics, text, 190), text, textColor);
			}
			catch (Exception e)
			{
				return null;
			}
		}
		return null;
	}
}