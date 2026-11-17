package com.potionfavourites;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class PotionFavouritesOverlay extends Overlay {
	private final Client client;

	private final PotionFavouritesPlugin plugin;

	public PotionFavouritesOverlay(Client client, PotionFavouritesPlugin plugin) {
		this.client = client;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics) {
		Point mouse = client.getMouseCanvasPosition();
		if (plugin.getMoving() == -1 || mouse == null) {
			return null;
		}

		plugin.row(plugin.getMoving()).ifPresent(source -> {
			Rectangle box = source.bounds();
			graphics.setColor(new Color(255, 255, 255, 60));
			graphics.fillRect(box.x, box.y, box.width, box.height);
			graphics.setColor(Color.WHITE);
			graphics.drawRect(box.x, box.y, box.width, box.height);
		});

		plugin.rows().stream()
			.filter(row -> row.getStart() != plugin.getMoving() && row.bounds().contains(mouse.getX(), mouse.getY()))
			.findFirst()
			.ifPresent(target -> {
				Rectangle box = target.bounds();
				graphics.setColor(new Color(119, 221, 119, 70));
				graphics.fillRect(box.x, box.y, box.width, box.height);
				graphics.setColor(new Color(119, 221, 119));
				graphics.drawRect(box.x, box.y, box.width, box.height);
			});

		return null;
	}
}
