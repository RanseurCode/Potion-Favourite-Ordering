# Potion Favourites

Re-order your favourites in the bank's potion store from the right-click menu.

Right-click a favourite and choose **Move**. The favourite is highlighted, and every other favourite now has **Swap with** at the top of its menu, so a left-click on one changes their places. Choose **Cancel move** on the highlighted favourite, or click anywhere else, to back out. The order is saved to your RuneLite profile, so it survives relogging and the potion store redrawing itself, for example after a withdraw.

Normal clicks are untouched until you choose Move.

## Options

| Option | Description |
| --- | --- |
| Insert mode | Off by default. When off, the second favourite you pick swaps places with the first. When on, the first is inserted at the second's position and the ones in between shift along. |

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
