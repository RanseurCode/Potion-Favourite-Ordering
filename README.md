# Potion Favourites

Re-order your favourites in the bank's potion store by dragging them.

Pick up a favourite and drop it onto another to change their places. The order is saved to your RuneLite profile, so it survives relogging and the potion store redrawing itself, for example after a withdraw.

A plain click still withdraws as normal. The drag only starts once you move the mouse a few pixels with the button held.

## Options

| Option | Description |
| --- | --- |
| Insert mode | Off by default. When off, dropping a favourite onto another swaps the two. When on, the dragged favourite is inserted at the position you drop it and the ones in between shift along. |

## Notes

- Only favourites can be re-ordered. The Potions and Unfinished Potions sections keep the game's order.
- Favourites you add later appear after the ones you have already ordered.
- Disabling the plugin puts the favourites back in the game's own order.

## Building

```
./gradlew build
```

Gradle 8.10 needs JDK 17 or 21. To run it in a development client, run `PotionFavouritesPluginTest`.

## License

BSD 2-Clause. See [LICENSE](LICENSE).
