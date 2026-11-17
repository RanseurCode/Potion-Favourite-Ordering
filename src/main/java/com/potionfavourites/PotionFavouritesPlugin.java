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
import net.runelite.api.MenuEntry;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Potion Favourites",
	description = "Re-order your favourites in the potion store from the right-click menu",
	tags = {"potion", "store", "favourites", "bank", "order"}
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
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	private PotionFavouritesOverlay overlay;

	@Getter(AccessLevel.PACKAGE)
	private int moving = -1;

	@Override
	protected void startUp() {
		overlay = new PotionFavouritesOverlay(client, this);
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown() {
		overlayManager.remove(overlay);
		moving = -1;
		clientThread.invoke(() -> {
			if (open()) {
				client.runScript(ScriptID.POTIONSTORE_BUILD);
			}
		});
	}

	@Subscribe
	public void onClientTick(ClientTick event) {
		if (!open()) {
			moving = -1;
		}
		arrange();
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event) {
		if (event.getScriptId() == ScriptID.POTIONSTORE_BUILD || event.getScriptId() == ScriptID.POTIONSTORE_DOSE_CHANGE) {
			arrange();
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event) {
		if (event.getMenuAction() != MenuAction.RUNELITE) {
			moving = -1;
		}
	}

	@Subscribe
	public void onPostMenuSort(PostMenuSort event) {
		if (!open()) {
			return;
		}

		for (MenuEntry entry : client.getMenuEntries()) {
			if (entry.getType() != MenuAction.CC_OP || entry.getParam1() != InterfaceID.Bankmain.POTIONSTORE_ITEMS) {
				continue;
			}

			Optional<Row> row = row(entry.getParam0() / 5 * 5).filter(found -> !found.isStar(entry.getParam0()));
			if (row.isPresent()) {
				offer(row.get().getStart(), entry.getTarget());
				return;
			}
		}
	}

	@Provides
	PotionFavouritesConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(PotionFavouritesConfig.class);
	}

	private void offer(int row, String target) {
		if (moving == -1) {
			client.createMenuEntry(1)
				.setOption("Move")
				.setTarget(target)
				.setType(MenuAction.RUNELITE)
				.onClick(entry -> moving = row);
		} else if (moving == row) {
			client.createMenuEntry(-1)
				.setOption("Cancel move")
				.setTarget(target)
				.setType(MenuAction.RUNELITE)
				.onClick(entry -> moving = -1);
		} else {
			client.createMenuEntry(-1)
				.setOption(config.insert() ? "Insert here" : "Swap with")
				.setTarget(target)
				.setType(MenuAction.RUNELITE)
				.onClick(entry -> {
					move(moving, row);
					moving = -1;
				});
		}
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
