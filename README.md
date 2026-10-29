# Schematic Cannon

A Minecraft Fabric mod for copying builds: save a structure as a schematic, preview it anywhere in your world and let the
Schematicannon build it block by block from the materials in its chests.

It is ported with the help of AI from the schematics of [Create](https://github.com/Creators-of-Create/Create) and [Create-Fabric](https://github.com/Fabricators-of-Create/Create) (MIT License), as a standalone mod without the
rest of Create and updated to Minecraft 26.3+. Schematic files from Create work as they are.

Note that Create's visual assets (unlike code) are under "All rights reserved license" and so they were replaced with AI generated alternatives.

## Features

- **Schematic and Pen**: select an area and save it as a schematic file
- **Schematic Table**: upload a schematic file to an Empty Schematic
- **Schematic**: preview the structure in the world, then move, rotate and mirror it with the tool bar above the hotbar
- **Schematicannon**: prints the schematic block by block, taking the blocks and gunpowder from adjacent inventories
- **Material checklist**: the cannon writes the list of required materials into a book
- **Creative Print** tool: places the whole schematic at once in creative mode
- Config screen through [Mod Menu](https://modrinth.com/mod/modmenu) (optional)

## How to Use

### Saving a structure

1. Right-click two corners with the **Schematic and Pen**. Hold Ctrl (your sprint key) and scroll to select corners in
   mid-air.
2. Ctrl + scroll on a face of the selection to resize it.
3. Right-click again and enter a name. The file is saved to the `schematics` folder of your game directory.

### Placing a schematic

1. Put an **Empty Schematic** into the **Schematic Table**, pick a file and press the upload button.
2. Hold the filled **Schematic** and right-click the ground to place the preview. Hold Left Alt and scroll to pick a tool
   (move, move up/down, rotate, mirror) and right-click to use it. Sneak + right-click opens a screen to type in the exact position.

### Printing it

1. Put the Schematic into the **Schematicannon** and place chests (or any other inventories) with the needed blocks and
   gunpowder next to it.
2. Press play. The cannon pauses when something is missing and continues by itself once it is there.
   - Water and lava are printed from buckets (the empty buckets go back into the chests), after all blocks so they
     can't flow out of the unfinished build. Only sources (and bubble columns) are placed, flowing fluid comes from
     them. Waterlogged blocks are printed dry and then filled from a water bucket. When a block was left out (with "Skip
     missing blocks", or one the cannon can't place, e.g. right next to it), the fluids are left out too, so they
     can't flow out of the gap.
3. Put a book into the cannon to get a checklist of the materials the schematic needs.

## Recipes

| Item              | Recipe                                                                                       |
|-------------------|----------------------------------------------------------------------------------------------|
| Empty Schematic   | Iron Nugget above Paper above a wooden slab                                                  |
| Schematic and Pen | Empty Schematic + Ink Sac + Copper Nugget                                                    |
| Schematic Table   | 3 wooden slabs on top of 2 Smooth Stone (in a T shape)                                       |
| Schematic copy    | A Schematic and an Empty Schematic, anywhere in the grid; the original is kept (like copying a book) |
| Schematicannon    | Iron Block on top of an Iron Block between 2 Copper Ingots, over Smooth Stone, Dispenser, Smooth Stone |

## Requirements

- Minecraft 26.3+
- Fabric Loader
- Fabric API
- Mod Menu (optional, for the config screen)

The mod is needed on both the server and the client.

## License

The code and assets are MIT licensed. See [LICENSE](LICENSE) for details.

## Credits

Created by BSHarou, based on Create by simibubi and The Create Team.
