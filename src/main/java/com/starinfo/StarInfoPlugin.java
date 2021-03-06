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

import com.google.common.collect.ImmutableMap;
import com.google.inject.Provides;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import javax.inject.Inject;
import net.runelite.api.AnimationID;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NullNpcID;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.coords.Angle;
import net.runelite.api.coords.Direction;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;

@PluginDescriptor(
	name = "Star Info",
	description = "Displays tier, number of miners, health % above shooting stars"
)
public class StarInfoPlugin extends Plugin
{

	private static final int NPC_ID = NullNpcID.NULL_10629;
	private static final int MAX_PLAYER_LOAD_DIST = 13;
	private static final Queue<Star> despawnQueue = new LinkedList<>();

//	private static final Set<Integer> dragonPickSpecAnims = ImmutableSet.of(6969);

	private static final Map<Integer, Double> pickAnims = ImmutableMap.<Integer, Double>builder().
		put(AnimationID.MINING_ADAMANT_PICKAXE, 3.0).
		put(AnimationID.MINING_TRAILBLAZER_PICKAXE_2, 17.0 / 6).
		put(AnimationID.MINING_TRAILBLAZER_PICKAXE, 17.0 / 6).
		put(AnimationID.MINING_BLACK_PICKAXE, 5.0).
		put(AnimationID.MINING_BRONZE_PICKAXE, 8.0).
		put(AnimationID.MINING_GILDED_PICKAXE, 3.0).
		put(AnimationID.MINING_CRYSTAL_PICKAXE, 2.75).
		put(AnimationID.MINING_3A_PICKAXE, 17.0 / 6).
		put(AnimationID.MINING_DRAGON_PICKAXE, 17.0 / 6).
		put(AnimationID.MINING_DRAGON_PICKAXE_OR, 17.0 / 6).
		put(AnimationID.MINING_DRAGON_PICKAXE_OR_TRAILBLAZER, 17.0 / 6).
		put(AnimationID.MINING_DRAGON_PICKAXE_UPGRADED, 17.0 / 6).
		put(AnimationID.MINING_INFERNAL_PICKAXE, 17.0 / 6).
		put(AnimationID.MINING_MITHRIL_PICKAXE, 5.0).
		put(AnimationID.MINING_IRON_PICKAXE, 7.0).
		put(AnimationID.MINING_RUNE_PICKAXE, 3.0).
		put(AnimationID.MINING_STEEL_PICKAXE, 6.0).
		put(AnimationID.MINING_TRAILBLAZER_PICKAXE_3, 17.0 / 6).
		build();

	private static final int MINING_CACHE_TIME = 13; // count player as a miner if they have done mining anim within this many ticks ago
	private static final Map<String, Integer> playerLastMined = new HashMap<>();

	@Inject
	private StarInfoOverlay starOverlay;

	public final List<Star> stars = new ArrayList<>();

	SampleEstimator estimator;

	@Inject
	private InfoBoxManager infoBoxManager;

	private StarInfoBox infoBox;

	@Inject
	private ItemManager itemManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	Client client;

	@Inject
	private StarInfoConfig starConfig;


	@Provides
	StarInfoConfig
	provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(StarInfoConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(starOverlay);
		starOverlay.updateConfig();
		estimator = new SampleEstimator(this);
	}

	@Override
	protected void shutDown() throws Exception
	{
		clear();
		refresh();
		overlayManager.remove(starOverlay);
		infoBox = null;
		estimator = null;
	}

	private void clear()
	{
		playerLastMined.clear();
		stars.clear();
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		if (event.getNpc().getId() != NPC_ID)
		{
			return;
		}
		for (Star star : stars)
		{
			if (star.getWorldPoint().equals(event.getNpc().getWorldLocation()))
			{
				star.setNpc(event.getNpc());
				refresh();
				return;
			}
		}
		stars.add(0, new Star(event.getNpc(), client.getWorld()));
		refresh();
	}


	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (event.getNpc().getId() != NPC_ID)
		{
			return;
		}
		for (Star star : stars)
		{
			if (star.getWorldPoint().equals(event.getNpc().getWorldLocation()))
			{
				star.setNpc(null);
				refresh();
				return;
			}
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		int tier = Star.getTier(event.getGameObject().getId());
		if (tier < 0)
		{
			return;
		}

		boolean newStar = false;
		Star star = null;
		for (Star s : stars)
		{
			if (s.getWorldPoint().equals(event.getGameObject().getWorldLocation()))
			{
				s.setObject(event.getGameObject());
				star = s;
				despawnQueue.remove(star);
				break;
			}
		}
		if (star == null)
		{
			star = new Star(event.getGameObject(), client.getWorld());
			stars.add(0, star);
			newStar = true;
		}

		if (newStar && starConfig.addToChat())
		{
			client.addChatMessage(ChatMessageType.CONSOLE, "", star.getMessage(), "");
		}
		refresh();
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		int tier = Star.getTier(event.getGameObject().getId());
		if (tier < 0)
		{
			return;
		}

		for (Star star : stars)
		{
			if (event.getGameObject().equals(event.getGameObject()) || event.getGameObject().getWorldLocation().equals(star.getWorldPoint()))
			{
				despawnQueue.add(star);
				break;
			}
		}
	}

	void updateMiners(Star star)
	{
		int distToStar = client.getLocalPlayer().getWorldLocation().distanceTo(new WorldArea(star.getWorldPoint(), 2, 2));
		if (distToStar > MAX_PLAYER_LOAD_DIST)
		{
			star.setMiners(Star.UNKNOWN_MINERS);
			return;
		}
		WorldArea areaH = new WorldArea(star.getWorldPoint().dx(-1), 4, 2);
		WorldArea areaV = new WorldArea(star.getWorldPoint().dy(-1), 2, 4);
		int count = 0;
		int tickCount = client.getTickCount();
//		List<PlayerInfo> miners = new ArrayList<>();
		for (Player p : client.getPlayers())
		{
			if (!p.getWorldLocation().isInArea2D(areaH, areaV)) // Skip players not next to the star
			{
				continue;
			}
			if (!facingObject(p.getWorldLocation(), p.getOrientation(), star.getWorldPoint()))
			{
				continue;
			}
			if (pickAnims.containsKey(p.getAnimation())) // count anyone that is doing mining animation
			{
				count++;
				playerLastMined.put(p.getName(), tickCount);
//				miners.add(new PlayerInfo(p.getName(), Estimator.NOT_FETCHED, -1, pickAnims.getOrDefault(p.getAnimation(), 3.0), hasGoldedPros(p.getPlayerComposition()), Instant.now()));
				continue;
			}
			if (p.getHealthRatio() < 0 || !playerLastMined.containsKey(p.getName()))
			{
				continue;
			}
			int ticksSinceMinedLast = tickCount - playerLastMined.get(p.getName());
			if (ticksSinceMinedLast < MINING_CACHE_TIME)
			{
				count++;
//				miners.add(new PlayerInfo(p.getName(), Estimator.NOT_FETCHED, -1, pickAnims.getOrDefault(p.getAnimation(), 3.0), hasGoldedPros(p.getPlayerComposition()), Instant.now()));
			}
		}
		star.setMiners(Integer.toString(count));
	}
//
//	@Subscribe
//	private void onAnimationChanged(AnimationChanged event)
//	{
//		if (dragonPickSpecAnims.contains(event.getActor().getAnimation()))
//		{
//			estimator.performedSpec((Player) event.getActor());
//		}
//	}

//	private boolean hasGoldedPros(PlayerComposition playerComposition)
//	{
//		if (playerComposition.getEquipmentId(KitType.HEAD) == GOLDEN_PROSPECTOR_HELMET)
//		{
//			return true;
//		}
//		if (playerComposition.getEquipmentId(KitType.BOOTS) == GOLDEN_PROSPECTOR_BOOTS)
//		{
//			return true;
//		}
//		if (playerComposition.getEquipmentId(KitType.TORSO) == GOLDEN_PROSPECTOR_JACKET)
//		{
//			return true;
//		}
//		if (playerComposition.getEquipmentId(KitType.LEGS) == GOLDEN_PROSPECTOR_LEGS)
//		{
//			return true;
//		}
//		return false;
//	}

	private boolean facingObject(WorldPoint p1, int orientation, WorldPoint p2)
	{
		Direction dir = new Angle(orientation).getNearestDirection();
		WorldPoint dif = p2.dx(-p1.getX()).dy(-p1.getY());
		switch (dir)
		{
			case NORTH:
				return dif.getY() > 0;
			case SOUTH:
				return dif.getY() < 0;
			case EAST:
				return dif.getX() > 0;
			case WEST:
				return dif.getX() < 0;
		}
		return false;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged state)
	{
		if (state.getGameState() == GameState.HOPPING || state.getGameState() == GameState.LOGGING_IN)
		{
			clear();
			refresh();
		}
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		if (stars.isEmpty())
		{
			return;
		}
		Iterator<Star> it = stars.iterator();
		boolean refresh = false;
		while (it.hasNext())
		{
			Star star = it.next();
			if (despawnQueue.contains(star) || client.getLocalPlayer().getWorldLocation().distanceTo(star.getWorldPoint()) > starConfig.removeDistance())
			{
				it.remove();
				refresh = true;
			}
		}

		if (!stars.isEmpty())
		{
			Star star = stars.get(0);
			if (starConfig.showMiners())
			{
				updateMiners(star);
			}
			estimator.update(star);
		}

		if (refresh)
		{
			refresh();
		}
	}

	public void refresh()
	{
		if (stars.isEmpty())
		{
			if (starConfig.showInfoBox())
			{
				infoBoxManager.removeInfoBox(infoBox);
			}
			if (starConfig.showHintArrow())
			{
				client.clearHintArrow();
			}
		}
		else
		{
			Star star = stars.get(0);
			if (starConfig.showInfoBox())
			{
				infoBoxManager.removeInfoBox(infoBox);
				infoBox = new StarInfoBox(itemManager.getImage(25547), this, star);
				infoBoxManager.addInfoBox(infoBox);
			}
			if (starConfig.showHintArrow())
			{
				client.setHintArrow(star.getWorldPoint());
			}
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (stars.isEmpty() || event.getType() != MenuAction.EXAMINE_OBJECT.getId() || !starConfig.copyToClipboard())
		{
			return;
		}

		final Tile tile = client.getScene().getTiles()[client.getPlane()][event.getActionParam0()][event.getActionParam1()];
		final TileObject tileObject = findTileObject(tile, event.getIdentifier());

		if (tileObject == null || !tile.getWorldLocation().equals(stars.get(0).getWorldPoint()) || Star.getTier(tileObject.getId()) < 0)
		{
			return;
		}

		client.createMenuEntry(-1)
			.setOption("Copy")
			.setTarget(event.getTarget())
			.setParam0(event.getActionParam0())
			.setParam1(event.getActionParam1())
			.setIdentifier(event.getIdentifier())
			.setType(MenuAction.RUNELITE)
			.onClick(this::copy);
	}

	private void copy(MenuEntry menuEntry)
	{
		if (stars.isEmpty())
		{
			return;
		}
		Star star = stars.get(0);
		String content = "W" + star.getWorld() + " T" + star.getTier() + " ";
		if (star.getHealth() >= 0)
		{
			content += star.getHealth() + "% ";
		}
		if (!star.getMiners().equals(Star.UNKNOWN_MINERS))
		{
			content += "- " + star.getMiners() + " Miners - ";
		}
		content += star.getLocation() + " " + DiscordTimeStamp.relativeTimeNow();

		final StringSelection stringSelection = new StringSelection(content);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Copied star information to clipboard.", "");
	}

	private TileObject findTileObject(Tile tile, int id)
	{
		if (tile == null)
		{
			return null;
		}
		for (GameObject object : tile.getGameObjects())
		{
			if (object != null && object.getId() == id)
			{
				return object;
			}
		}
		return null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("starinfoplugin"))
		{
			return;
		}
		switch (event.getKey())
		{
			case StarInfoConfig.SHOW_MINERS:
				if (!stars.isEmpty() && starConfig.showMiners())
				{
					updateMiners(stars.get(0));
				}
				break;
			case StarInfoConfig.TEXT_COLOR_KEY:
				starOverlay.updateConfig();
				break;
			case StarInfoConfig.INFO_BOX_KEY:
				if (starConfig.showInfoBox())
				{
					refresh();
				}
				else
				{
					infoBoxManager.removeInfoBox(infoBox);
					infoBox = null;
				}
				break;
			case StarInfoConfig.HINT_ARROW_KEY:
				if (starConfig.showHintArrow())
				{
					refresh();
				}
				else
				{
					client.clearHintArrow();
				}
				break;
		}
	}
}