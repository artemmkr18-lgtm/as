package ez.minar.system.features.misc;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.menu.cosmetics.CosmeticsScreen;
import ez.minar.system.settings.impl.ButtonSetting;
import net.minecraft.util.Identifier;
import net.fabricmc.loader.api.FabricLoader;
import java.io.File;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

@NewFunction(name = "Cosmetics", desc = "Cosmetic models for items", category = Category.MISC)
public class Cosmetics extends Function {
    public static final String[] SWORD_MODELS = {
            "Abominable Blade", "Abominable Great Saber", "Abominable Scythe", "Acidic Cleaver",
            "Amethyst Shuriken", "Ancient Royal Great Sword", "Aquatic Sacred Blade", "Arcanethyst",
            "Ashura's Blade", "Awakened Lichblade", "Blood Edge", "Bloody Death", "Bramblethorn",
            "Brimstone Claymore", "Carian Knight's Sword", "Chrono Blade", "Corrupted Mythic Blade",
            "Creation Splitter", "Crescent Rose", "Cyber Katana", "Cyber Mantis Blade", "Cyber Sword",
            "Cybernetic Chainsaw Blade", "Cybernetic Katana", "Cybernetic Knife", "Dainsleif",
            "Dark Blade", "Dark Cleaver", "Death Knight's Dagger", "Death Knight's Sword",
            "Demigod's Unholy Blade", "Demigod's Unholy Halberd", "Demon Lord's Great Axe",
            "Demon Lord's Sword", "Demonic Blade", "Demonic Cleaver", "Divine Axe Rhitta",
            "Divine Justice", "Divine Punisher", "Divine Reaper", "Dragon Slaying blade",
            "Edge Of The Astral Plane", "Emberblade", "Enigma", "Epic Sword", "Estoc",
            "Fallen God's Spear", "Fallen God's Sword", "Floral Longsword", "Floral Sabre",
            "Forest Guardian's Glaive", "Frost Axe", "Frost Blade", "Frost Scythe", "Hearthflame",
            "Hero Sword", "Holy Moonlight Sword", "Hornet's Needle", "Icewhisper", "Jade Halberd",
            "Katana", "Legendary Sword", "Longsword", "Magi Scythe", "Masamune", "Mjolnir",
            "Molten Blade", "Molten Sword", "Muramasa", "Mystical Spellblade", "Mythic Blade",
            "Ocean's Rage", "Partisan", "Pharaoh's Treasure", "Pheonix Grace", "Plague Longsword",
            "Power Fuse Hammer", "Power Fuse Sword", "Requiem of the Ninth Abyss", "Ribbon Cleaver",
            "Righteous Relic", "Rivers Of Blood", "Royal Chakram", "Royal Rapier", "Sabre",
            "Scissor Blade", "Sculk Cleaver", "Sculk Scythe", "Sculk Sword", "Sentinel's Will",
            "Silverine Blade", "Soul Claws", "Soul Collector", "Soul Devourer", "Soul Edge",
            "Soul Harvester", "Soul Stealer", "Soulrender", "Star's Edge", "Steel Sword",
            "Stop Sign", "Storm Bringer", "Storm's Edge", "Sunbreak", "Tengen's Blade",
            "Terra Blade", "Thousand Demon Daggers", "Thunder Bringer", "Thunderbrand", 
            "True Excalibur", "Vampiric Needle", "Wakizashi", "Watcher Claymore",
            "Watching Warglaive", "Waxweaver", "Whisperwind", "Wickpiercer", "Wraith Scythe", "Yoru"
    };
    public static final String[] PLAYER_MODELS = {
            "Aria", "Mita", "Water_dragon", "Hazel", "Hymenopteran", "aoandon", "Repo", "Wheelchair", "mosasaur", "Sahur", "Miku", "aquatic"
    };

    private boolean renderingPreview;
    private Object lastClientWorld;
    private final ButtonSetting menu = new ButtonSetting("Cosmetics menu", "Open", "Katana", () ->
            mc.setScreen(new CosmeticsScreen(mc.currentScreen, this)));
    private String selectedModel = "None";
    private String previewModel = "None";
    private final Map<String, org.figuramc.figura.avatar.UserData> previewAvatars = new HashMap<>();

    public Cosmetics() {
        addSettings(menu);
        FiguraRuntimeGate.setEnabled(false);
        
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world != lastClientWorld) {
                lastClientWorld = client.world;
                if (isEnabled() && client.world != null
                        && selectedModel != null && !selectedModel.equals("None")) {
                    // Figura clears local avatars while the client swaps worlds.
                    // Restore the selected cosmetic once the new world is available.
                    loadPlayerModel(selectedModel);
                }
            }

        });
        
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (isEnabled()) {
                org.figuramc.figura.backend2.NetworkStuff.auth();
            }
        });
        
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            lastClientWorld = null;
            unloadAllPreviewModels();
            org.figuramc.figura.avatar.AvatarManager.clearAllAvatars();
            org.figuramc.figura.lua.FiguraLuaPrinter.clearPrintQueue();
            org.figuramc.figura.backend2.NetworkStuff.unsubscribeAll();
        });
    }

    @Override
    public void onEnable() {
        FiguraRuntimeGate.setEnabled(true);
        if (selectedModel != null && !selectedModel.equals("None")) {
            loadPlayerModel(selectedModel);
        }
        if (mc.getNetworkHandler() != null) {
            org.figuramc.figura.backend2.NetworkStuff.auth();
        }
    }

    @Override
    public void onDisable() {
        FiguraRuntimeGate.setEnabled(false);
        previewModel = "None";
        unloadAllPreviewModels();
        ez.minar.system.menu.cosmetics.FiguraPreviewAvatarContext.clear();
        org.figuramc.figura.avatar.AvatarManager.clearAllAvatars();
        org.figuramc.figura.lua.FiguraLuaPrinter.clearPrintQueue();
        org.figuramc.figura.backend2.NetworkStuff.unsubscribeAll();
        org.figuramc.figura.backend2.NetworkStuff.disconnect(null);
    }

    public void refreshAnimationsForFrame() {
        if (!isEnabled()) {
            return;
        }
        org.figuramc.figura.avatar.AvatarManager.executeAll(
                "refreshBBAnimations",
                avatar -> {
                    if (avatar.renderer != null) {
                        // Figura's deferred renderer leaves item-pivot matrices in the
                        // queue until the next frame. With a fast-moving F5 camera that
                        // makes the held item use the previous camera angle and visibly
                        // lag behind the player model. Let Minecraft attach hand items
                        // to the current interpolated arm pose instead.
                        avatar.renderer.pivotCustomizations.remove(
                                org.figuramc.figura.model.ParentType.RightItemPivot
                        );
                        avatar.renderer.pivotCustomizations.remove(
                                org.figuramc.figura.model.ParentType.LeftItemPivot
                        );
                    }
                    avatar.clearAnimations();
                    avatar.applyAnimations();
                }
        );
    }

    public String getSelectedSword() {
        return menu.getStoredValue();
    }

    public void setSelectedSword(String selectedSword) {
        for (String model : SWORD_MODELS) {
            if (model.equals(selectedSword)) {
                menu.setStoredValue(selectedSword);
                return;
            }
        }
    }

    public String getSelectedModel() {
        return selectedModel;
    }

    public void setSelectedModel(String model) {
        this.selectedModel = model;
        this.previewModel = "None";
        if (isEnabled() && model != null && !model.equals("None")) {
            loadPlayerModel(model);
        } else {
            org.figuramc.figura.avatar.AvatarManager.clearAvatars(org.figuramc.figura.FiguraMod.getLocalPlayerUUID());
        }
    }

    public void loadPreviewModel(String model) {
        if (model == null || model.equals("None")) {
            return;
        }
        if (model.equals(selectedModel)) {
            restoreSelectedModelAfterPreview();
            return;
        }
        if (model.equals(previewModel)) {
            return;
        }
        previewModel = model;
        loadPlayerModel(model);
    }

    public void loadAllPreviewModels() {
        for (String model : PLAYER_MODELS) {
            if (previewAvatars.containsKey(model)) {
                continue;
            }
            extractModelIfNeeded(model);
            File modelFolder = FabricLoader.getInstance().getGameDir().resolve("models").resolve(model).toFile();
            if (!modelFolder.exists()) {
                continue;
            }
            org.figuramc.figura.avatar.UserData user = new PreviewUserData(
                    org.figuramc.figura.FiguraMod.getLocalPlayerUUID()
            );
            previewAvatars.put(model, user);
            org.figuramc.figura.avatar.local.LocalAvatarLoader.loadAvatar(modelFolder.toPath(), user);
        }
    }

    public void unloadAllPreviewModels() {
        for (org.figuramc.figura.avatar.UserData user : previewAvatars.values()) {
            if (user instanceof PreviewUserData previewUser) {
                previewUser.unload();
            } else {
                user.clear();
            }
        }
        previewAvatars.clear();
    }

    public org.figuramc.figura.avatar.Avatar getPreviewAvatar(String model) {
        org.figuramc.figura.avatar.UserData user = previewAvatars.get(model);
        return user == null ? null : user.getMainAvatar();
    }

    private static final class PreviewUserData extends org.figuramc.figura.avatar.UserData {
        private volatile boolean active = true;

        private PreviewUserData(java.util.UUID id) {
            super(id);
        }

        @Override
        public void loadAvatar(net.minecraft.nbt.NbtCompound nbt) {
            if (active) {
                super.loadAvatar(nbt);
            }
        }

        private void unload() {
            active = false;
            clear();
        }
    }

    public void restoreSelectedModelAfterPreview() {
        if (previewModel.equals("None")) {
            return;
        }
        previewModel = "None";
        if (selectedModel != null && !selectedModel.equals("None")) {
            loadPlayerModel(selectedModel);
        } else {
            org.figuramc.figura.avatar.AvatarManager.clearAvatars(org.figuramc.figura.FiguraMod.getLocalPlayerUUID());
        }
    }

    private void loadPlayerModel(String model) {
        extractModelIfNeeded(model);
        File modelFolder = FabricLoader.getInstance().getGameDir().resolve("models").resolve(model).toFile();
        if (modelFolder.exists()) {
            org.figuramc.figura.avatar.AvatarManager.loadLocalAvatar(modelFolder.toPath());
        }
    }

    private void extractModelIfNeeded(String model) {
        if (model == null || model.equals("None")) return;
        File targetFolder = FabricLoader.getInstance().getGameDir().resolve("models").resolve(model).toFile();
        if (!targetFolder.exists()) {
            FabricLoader.getInstance().getModContainer("minar").ifPresent(mod -> {
                java.util.Optional<java.nio.file.Path> modelPathOpt = mod.findPath("assets/minecraft/player_models/" + model);
                modelPathOpt.ifPresent(modelPath -> {
                    try {
                        copyDirectory(modelPath, targetFolder.toPath());
                    } catch (java.io.IOException e) {
                        e.printStackTrace();
                    }
                });
            });
        }
    }

    private void copyDirectory(java.nio.file.Path source, java.nio.file.Path target) throws java.io.IOException {
        java.nio.file.Files.walkFileTree(source, new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(java.nio.file.Path dir, java.nio.file.attribute.BasicFileAttributes attrs) throws java.io.IOException {
                java.nio.file.Path targetDir = target.resolve(source.relativize(dir).toString());
                if (!java.nio.file.Files.exists(targetDir)) {
                    java.nio.file.Files.createDirectories(targetDir);
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFile(java.nio.file.Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws java.io.IOException {
                java.nio.file.Files.copy(file, target.resolve(source.relativize(file).toString()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    public ItemStack getRenderStack(ItemStack stack) {
        if (renderingPreview || !isEnabled() || stack.isEmpty() || !stack.isIn(ItemTags.SWORDS)
                || !isLocalPlayerStack(stack)) {
            return stack;
        }
        ItemStack copy = stack.copy();
        applyModel(copy, getSelectedSword());
        return copy;
    }

    private boolean isLocalPlayerStack(ItemStack stack) {
        if (mc.player == null) {
            return false;
        }
        for (int slot = 0; slot < mc.player.getInventory().size(); slot++) {
            if (mc.player.getInventory().getStack(slot) == stack) {
                return true;
            }
        }
        return mc.player.getMainHandStack() == stack || mc.player.getOffHandStack() == stack;
    }

    public ItemStack createPreviewStack(String model) {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        applyModel(stack, model);
        return stack;
    }

    public void drawPreview(DrawContext context, ItemStack stack, int x, int y) {
        renderingPreview = true;
        try {
            context.drawItemWithoutEntity(stack, x, y);
        } finally {
            renderingPreview = false;
        }
    }

    private void applyModel(ItemStack stack, String model) {
        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(
                List.of(), List.of(), List.of(model), List.of()
        ));
    }
}
