# Schematic Cannon

Standalone Fabric mod containing only the schematic feature set of Create: schematics, Schematic and Pen, Schematic Table, Schematicannon, their screens/UI, and schematic loading/uploading.

## Code layout (`net.harou.schematiccannon`)
- root: `SchematicCannon` (main entrypoint), `ModConfig`
- `registry/`: blocks, items, block entities, menus, data components
- `schematic/`: schematic item, export, printer, processor, item requirements, server upload handling; `level/`: `SchematicLevel` (detached level the template is placed into)
- `cannon/`, `table/`: blocks, block entities, menus, launched items, material checklist
- `network/`: packets (`ServerboundPacket` + `ModPackets`)
- `foundation/`: `SyncedBlockEntity`, `BlockEntityMenu`, `FilteredSlot`, `MenuOpenData`
- `util/`: small helpers taken from Create/Catnip
- `client/`: `SchematicCannonClient` (client entrypoint and event wiring), schematic handler/renderer/transformation, Schematic and Pen handler, upload loader, cannon renderer; `tools/`, `screen/`, `gui/` (+ `widget/`), `outline/`, `render/`
- `mixin/`: `MouseHandlerMixin`, `KeyboardHandlerMixin`

## References (in `..\minecraft-mods`)
- `create-fabric/`: Create Fabric 1.20.1 (original source; Fabric-specific approaches, e.g. transfer API usage).
- `third-party/create-1.21.1/`: upstream Create NeoForge 1.21.1 (primary base for the port: data components, StreamCodec packets).
- `third-party/ponder-1.21.1/`: Ponder + Catnip 1.21.1 (`common/` and `fabric/`): `SchematicLevel`, outliner, LerpedFloat, GUI base classes.
- `third-party/minecraft-<version>/`: decompiled MC sources (1.21.1 ... 1.21.11-mojang, 26.1.2, 26.2, 26.3); `minecraft-<version>-data/`: a few vanilla data/asset samples. New versions: `./gradlew genSources`, then unzip the `*-sources.jar` from `.gradle/loom-cache/minecraftMaven` (it has our access widener applied).
- `third-party/fabric-<version>/`: Fabric API sources (1.21.1, 1.21.5, 1.21.10, 26.1.2).
- `mod-version-update.md`: general update guide (mainly for 26.x, partially applicable).

## Build
- `./gradlew build` (unobfuscated `net.fabricmc.fabric-loom` 1.17, Gradle 9.5, Java 25, Fabric Loader >= 0.19.3). The access widener uses the `classTweaker v1 official` header.
- Global rules from the user's CLAUDE.md apply (git read-only, no runClient, no tests unless asked, ask before downloads).
