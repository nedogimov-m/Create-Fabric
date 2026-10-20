// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE.
package net.createmod.ponder.foundation.registration;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

import net.createmod.ponder.Ponder;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.api.registration.SceneRegistryAccess;
import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public class PonderSceneRegistry implements SceneRegistryAccess {

    private record CachedSchematic(CompoundTag fixed, int originalDataVersion) {}
    private static final Map<Identifier, CachedSchematic> SCHEMATIC_CACHE = new java.util.HashMap<>();
    private static ResourceManager schematicResources;

	private final PonderLocalization localization;
	private final Multimap<Identifier, StoryBoardEntry> scenes;

	private boolean allowRegistration = true;

	public PonderSceneRegistry(PonderLocalization localization) {
		this.localization = localization;
		scenes = LinkedHashMultimap.create();
	}

	public void clearRegistry() {
		scenes.clear();
		allowRegistration = true;
        SCHEMATIC_CACHE.clear();
        schematicResources = null;
	}

	//

	public void addStoryBoard(StoryBoardEntry entry) {
		if (!allowRegistration)
			throw new IllegalStateException("Registration Phase has already ended!");

		scenes.put(entry.getComponent(), entry);
	}

	//

	@Override
	public Collection<Map.Entry<Identifier, StoryBoardEntry>> getRegisteredEntries() {
		return scenes.entries();
	}

	@Override
	public boolean doScenesExistForId(Identifier id) {
		return scenes.containsKey(id);
	}

	//

	@Override
	public List<PonderScene> compile(Identifier id) {
		if (PonderIndex.editingModeActive())
			PonderIndex.reload();

		Collection<StoryBoardEntry> entries = scenes.get(id);

		if (entries.isEmpty()) return Collections.emptyList();

		return compile(entries);

	}

	@Override
	public List<PonderScene> compile(Collection<StoryBoardEntry> entries) {
		if (PonderIndex.editingModeActive()) {
			localization.clearShared();
			PonderIndex.gatherSharedText();
		}

		List<PonderScene> scenes = new ArrayList<>();
        var host=Minecraft.getInstance().level;
        if(host==null)throw new IllegalStateException("Ponder scene compilation requires a current client world");

		for (StoryBoardEntry storyBoard : entries) {
			StructureTemplate activeTemplate = loadSchematic(storyBoard.getSchematicLocation());
			PonderLevel level = new PonderLevel(BlockPos.ZERO, host);
			if (!level.place(activeTemplate, BlockPos.ZERO, new StructurePlaceSettings()))
                throw new IllegalStateException("Ponder structure could not be placed: " + storyBoard.getSchematicLocation());
			level.createBackup();
			PonderScene scene = compileScene(localization, storyBoard, level);
			scene.begin();
			scenes.add(scene);
		}

		return scenes;
	}

	public static PonderScene compileScene(PonderLocalization localization, StoryBoardEntry sb, @Nullable PonderLevel level) {
		PonderScene scene = new PonderScene(level, localization, sb.getNamespace(), sb.getComponent(), sb.getTags(),
			sb.getOrderingEntries());
		SceneBuilder builder = scene.builder();
		sb.getBoard().program(builder, scene.getSceneBuildingUtil());
		return scene;
	}

	public static StructureTemplate loadSchematic(Identifier location) {
		return loadSchematic(Minecraft.getInstance().getResourceManager(), location);
	}

	public static StructureTemplate loadSchematic(ResourceManager resourceManager, Identifier location) {
        if (schematicResources != resourceManager) {
            SCHEMATIC_CACHE.clear();
            schematicResources = resourceManager;
        }
        CachedSchematic cached = SCHEMATIC_CACHE.get(location);
        if (cached != null) {
            try { return loadSchematic(cached); }
            catch (IOException e) { throw new IllegalStateException("Failed to decode cached Ponder schematic: " + location, e); }
        }
		String namespace = location.getNamespace();
		String path = "ponder/" + location.getPath() + ".nbt";
		Identifier location1 = Identifier.fromNamespaceAndPath(namespace, path);

		Optional<Resource> optionalResource = resourceManager.getResource(location1);
		if (optionalResource.isEmpty()) {
            throw new IllegalStateException("Ponder schematic missing: " + location1);
		}

		Resource resource = optionalResource.get();
		try (InputStream inputStream = resource.open()) {
            CachedSchematic data = readSchematic(inputStream);
            StructureTemplate result = loadSchematic(data);
            SCHEMATIC_CACHE.put(location, data);
            return result;
		} catch (IOException e) {
            throw new IllegalStateException("Failed to read Ponder schematic: " + location1, e);
        }
	}

	public static StructureTemplate loadSchematic(InputStream resourceStream) throws IOException {
        return loadSchematic(readSchematic(resourceStream));
    }

    private static CachedSchematic readSchematic(InputStream resourceStream) throws IOException {
		DataInputStream stream = new DataInputStream(new BufferedInputStream(new GZIPInputStream(resourceStream)));
		CompoundTag nbt = NbtIo.read(stream, NbtAccounter.create(0x20000000L));
        int originalDataVersion = net.minecraft.nbt.NbtUtils.getDataVersion(nbt, 500);
        CompoundTag fixed = net.minecraft.util.datafix.DataFixTypes.STRUCTURE.updateToCurrentVersion(
            net.minecraft.util.datafix.DataFixers.getDataFixer(), nbt, originalDataVersion);
        return new CachedSchematic(fixed, originalDataVersion);
    }

    private static StructureTemplate loadSchematic(CachedSchematic data) throws IOException {
        // Templates and block-entity normalization remain private to each scene.
        // Only the decoded resource bytes/data-fix result are shared between openings.
        StructureTemplate t = new StructureTemplate();
        CompoundTag fixed = data.fixed().copy();
        // Native NbtUtils returns AIR for an unknown palette identifier. Ponder scenes
        // must report an absent ported block instead of silently compiling a different world.
        var palettes=fixed.getList("palettes");
        if(palettes.isPresent()) {
            for(var palette:palettes.get()) {
                if(!(palette instanceof ListTag states))throw new IOException("Invalid Ponder structure palette list");
                validatePalette(states);
            }
        } else validatePalette(fixed.getListOrEmpty("palette"));
        normalizeLegacyBlockEntityPositions(fixed);
        var hostLevel = Minecraft.getInstance().level;
        if (hostLevel == null) throw new IOException("Ponder legacy block-entity decoding requires a current client world");
        PonderLegacyBlockEntityData.normalize(fixed, data.originalDataVersion(), hostLevel.registryAccess());
        t.load(BuiltInRegistries.BLOCK, fixed);
		return t;
	}

    private static void normalizeLegacyPaletteProperties(Identifier id,CompoundTag state) throws IOException {
        if(!id.toString().equals("create:nixie_tube"))return;
        var properties=state.getCompound("properties").orElse(null);
        if(properties==null||!properties.contains("ceiling"))return;
        var legacy=properties.getString("ceiling");
        if(legacy.isEmpty()||!legacy.get().equals("true")&&!legacy.get().equals("false"))
            throw new IOException("Invalid legacy Ponder Nixie attachment: "+legacy.orElse("<missing>"));
        String face=legacy.get().equals("true")?"ceiling":"floor";
        if(properties.contains("double_face")&&!properties.getString("double_face").filter(face::equals).isPresent())
            throw new IOException("Conflicting legacy/native Ponder Nixie attachments");
        properties.putString("double_face",face);properties.remove("ceiling");
    }

    /** Original Create Ponder templates use XYZ compounds; native BlockPos.CODEC uses an int stream. */
    private static void normalizeLegacyBlockEntityPositions(CompoundTag template) throws IOException {
        for (var entry : template.getListOrEmpty("blocks")) {
            if (!(entry instanceof CompoundTag block) || !(block.get("nbt") instanceof CompoundTag data)) continue;
            String type = data.getString("id").orElse("");
            if (!type.startsWith("create:")) continue;
            for (String field : List.of("Source", "Controller", "LastKnownPos", "TargetTrack", "EnginePos",
                    "TargetOffset", "LastPos", "AffectedAreaFrom", "AffectedAreaTo", "BoundLocation")) {
                // Display-link Source is a source-provider configuration, not a kinetic position.
                if (field.equals("Source") && type.equals("create:display_link")) continue;
                if (!(data.get(field) instanceof CompoundTag original)) continue;
                if (!original.keySet().equals(java.util.Set.of("X", "Y", "Z"))
                        || !(original.get("X") instanceof net.minecraft.nbt.IntTag)
                        || !(original.get("Y") instanceof net.minecraft.nbt.IntTag)
                        || !(original.get("Z") instanceof net.minecraft.nbt.IntTag))
                    throw new IOException("Invalid original Ponder position: " + type + "." + field);
                data.store(field, BlockPos.CODEC, new BlockPos(original.getInt("X").orElseThrow(),
                        original.getInt("Y").orElseThrow(), original.getInt("Z").orElseThrow()));
            }
        }
    }

    private static void validatePalette(ListTag palette) throws IOException {
        for(var entry:palette) {
            if(!(entry instanceof CompoundTag state))throw new IOException("Invalid Ponder block-state entry");
            var name=state.getString("id");
            var id=name.isEmpty()?null:Identifier.tryParse(name.get());
            if(id==null||!BuiltInRegistries.BLOCK.containsKey(id))
                throw new IOException("Ponder block is not registered: "+name.orElse("<missing id>"));
            normalizeLegacyPaletteProperties(id,state);
            var definition=BuiltInRegistries.BLOCK.getValue(id).getStateDefinition();
            for(var properties:state.getCompound("properties").stream().toList()) {
                for(var key:properties.keySet()) {
                    var property=definition.getProperty(key);
                    var value=properties.getString(key);
                    if(property==null||value.isEmpty()||property.getValue(value.get()).isEmpty())
                        throw new IOException("Invalid Ponder block property: "+id+"["+key+"="+value.orElse("<missing>")+"]");
                }
            }
        }
    }
}
