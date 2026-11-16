package com.potionfavourites;

import com.google.inject.Provides;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Potion Favourites",
	description = "Drag a favourite onto another in the potion store to re-order them",
	tags = {"potion", "store", "favourites", "bank", "drag"}
)
public class PotionFavouritesPlugin extends Plugin {
	@Value
	static class Row {
		int start;

		Widget[] children;

		Rectangle bounds() {
			return children[0].getBounds();
		}

		boolean isStar(int child) {
			int index = child - start;
			return index >= 0 && index < children.length && removes(children[index]);
		}
	}

	@Inject
	private Client client;

	@Inject
	private PotionFavouritesConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	private PotionFavouritesOverlay overlay;

	private MenuOptionClicked held;

	private Point origin;

	@Getter(AccessLevel.PACKAGE)
	private int start;

	@Getter(AccessLevel.PACKAGE)
	private boolean dragging;

	private boolean replaying;

	@Override
	protected void startUp() {
		overlay = new PotionFavouritesOverlay(client, this);
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown() {
		overlayManager.remove(overlay);
		held = null;
		dragging = false;
	}

	@Subscribe
	public void onClientTick(ClientTick event) {
		arrange();
		track();
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event) {
		if (event.getScriptId() == 6080 || event.getScriptId() == 6555) {
			arrange();
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event) {
		Point mouse = client.getMouseCanvasPosition();
		if (replaying || mouse == null || event.getMenuAction() != MenuAction.CC_OP || event.getParam1() != InterfaceID.Bankmain.POTIONSTORE_ITEMS || !open()) {
			return;
		}

		int row = event.getParam0() / 5 * 5;
		if (row(row).filter(found -> !found.isStar(event.getParam0())).isEmpty()) {
			return;
		}

		event.consume();
		held = event;
		start = row;
		origin = mouse;
		dragging = false;
	}

	@Provides
	PotionFavouritesConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(PotionFavouritesConfig.class);
	}

	private void track() {
		Point mouse = client.getMouseCanvasPosition();
		if (held == null || mouse == null) {
			return;
		}

		dragging |= Math.hypot(mouse.getX() - origin.getX(), mouse.getY() - origin.getY()) > 6;
		if (client.getMouseCurrentButton() != 0) {
			return;
		}

		MenuOptionClicked click = held;
		boolean moved = dragging;
		held = null;
		dragging = false;
		if (moved) {
			rows().stream()
				.filter(row -> row.bounds().contains(mouse.getX(), mouse.getY()))
				.findFirst()
				.ifPresent(target -> move(start, target.getStart()));
			return;
		}

		replaying = true;
		client.menuAction(click.getParam0(), click.getParam1(), click.getMenuAction(), click.getId(), click.getMenuEntry().getItemId(), click.getMenuOption(), click.getMenuTarget());
		replaying = false;
	}

	private void arrange() {
		List<Row> rows = open() ? rows() : List.of();
		List<Integer> natural = natural();
		if (rows.size() < 2 || rows.size() != natural.size()) {
			return;
		}

		List<int[]> slots = rows.stream()
			.map(row -> new int[]{row.children[0].getOriginalX(), row.children[0].getOriginalY()})
			.sorted(Comparator.<int[]>comparingInt(slot -> slot[1]).thenComparingInt(slot -> slot[0]))
			.collect(Collectors.toList());

		List<Integer> shown = shown(natural);
		boolean moved = false;
		for (int slot = 0; slot < shown.size(); slot++) {
			Widget[] children = rows.get(natural.indexOf(shown.get(slot))).children;
			int dx = slots.get(slot)[0] - children[0].getOriginalX();
			int dy = slots.get(slot)[1] - children[0].getOriginalY();
			moved |= dx != 0 || dy != 0;
			for (Widget child : children) {
				child.setOriginalX(child.getOriginalX() + dx);
				child.setOriginalY(child.getOriginalY() + dy);
				child.revalidate();
			}
		}

		if (moved) {
			Widget list = client.getWidget(InterfaceID.Bankmain.POTIONSTORE_ITEMS);
			list.revalidate();
			list.revalidateScroll();
		}
	}

	private void move(int from, int to) {
		List<Row> rows = rows();
		List<Integer> natural = natural();
		int first = indexOf(rows, from);
		int second = indexOf(rows, to);
		if (first == -1 || second == -1 || first == second || rows.size() != natural.size()) {
			return;
		}

		List<Integer> shown = shown(natural);
		int source = shown.indexOf(natural.get(first));
		int target = shown.indexOf(natural.get(second));
		if (config.insert()) {
			shown.add(target, shown.remove(source));
		} else {
			Integer held = shown.get(source);
			shown.set(source, shown.get(target));
			shown.set(target, held);
		}

		configManager.setConfiguration(
			PotionFavouritesConfig.GROUP,
			PotionFavouritesConfig.ORDER,
			shown.stream().map(String::valueOf).collect(Collectors.joining(","))
		);

		arrange();
	}

	private boolean open() {
		Widget container = client.getWidget(InterfaceID.Bankmain.POTIONSTORE_CONTAINER);
		return container != null && !container.isHidden();
	}

	List<Row> rows() {
		Widget list = client.getWidget(InterfaceID.Bankmain.POTIONSTORE_ITEMS);
		Widget[] children = list == null ? null : list.getDynamicChildren();
		List<Row> rows = new ArrayList<>();
		for (int start = 0; children != null && start + 5 <= children.length; start += 5) {
			Widget[] block = Arrays.copyOfRange(children, start, start + 5);
			if (Arrays.stream(block).anyMatch(PotionFavouritesPlugin::removes)) {
				rows.add(new Row(start, block));
			}
		}
		return rows;
	}

	Optional<Row> row(int start) {
		return rows().stream().filter(row -> row.getStart() == start).findFirst();
	}

	private List<Integer> shown(List<Integer> natural) {
		List<Integer> saved = Arrays.stream(config.order().split(","))
			.filter(part -> !part.isEmpty())
			.map(Integer::valueOf)
			.collect(Collectors.toList());
		List<Integer> shown = new ArrayList<>(natural);
		shown.sort(Comparator.comparingInt(value -> saved.contains(value) ? saved.indexOf(value) : saved.size()));
		return shown;
	}

	private List<Integer> natural() {
		Set<Integer> chosen = new HashSet<>();
		for (int varp = VarPlayerID.POTIONSTORE_FAVOURITE_1; varp <= VarPlayerID.POTIONSTORE_FAVOURITE_8; varp++) {
			chosen.add(client.getVarpValue(varp));
		}

		List<Integer> natural = new ArrayList<>();
		for (int list : new int[]{4826, 4829}) {
			for (int potion : client.getEnum(list).getIntVals()) {
				int value = client.getEnum(potion).getIntValue(1);
				if (chosen.contains(value)) {
					natural.add(value);
				}
			}
		}
		return natural;
	}

	private int indexOf(List<Row> rows, int start) {
		for (int index = 0; index < rows.size(); index++) {
			if (rows.get(index).getStart() == start) {
				return index;
			}
		}
		return -1;
	}

	private static boolean removes(Widget child) {
		String[] actions = child == null ? null : child.getActions();
		return actions != null && Arrays.asList(actions).contains("Remove Favourite");
	}
}
