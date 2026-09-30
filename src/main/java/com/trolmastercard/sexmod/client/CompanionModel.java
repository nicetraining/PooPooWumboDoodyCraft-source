package com.trolmastercard.sexmod.client;

import com.trolmastercard.sexmod.entity.CompanionEntity;
import com.trolmastercard.sexmod.entity.CompanionKind;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

import java.util.Locale;
import java.util.Set;

public final class CompanionModel extends GeoModel<CompanionEntity> {
    private static final Set<String> ALEX_ARM_BONES = Set.of(
            "leftarmalex",
            "leftlowerarmalex",
            "rightarmalex",
            "rightlowerarmalex"
    );
    private static final Set<String> STEVE_ARM_BONES = Set.of(
            "leftarmsteve",
            "leftlowerarmsteve",
            "rightarmsteve",
            "rightlowerarmsteve"
    );
    private static final Set<String> COMMON_SCENE_BONES = Set.of(
            "steve",
            "items",
            "cum",
            "weapon",
            "weaponstart",
            "weaponend",
            "energyballr",
            "energyballl",
            "egg"
    );

    @Override
    public ResourceLocation getModelResource(CompanionEntity entity) {
        return entity.kind().modelResource(entity.outfit());
    }

    @Override
    public ResourceLocation getTextureResource(CompanionEntity entity) {
        return entity.kind().textureResource();
    }

    @Override
    public ResourceLocation getAnimationResource(CompanionEntity entity) {
        return entity.kind().animationResource();
    }

    /**
     * The original renderers changed the visibility of armour, player proxies,
     * props and cosmetic variants every frame. Rendering the old geometry
     * without those rules draws all variants on top of each other. This
     * establishes one clean, clothed baseline until the full equipment and
     * scene state machines are ported.
     */
    @Override
    public void setCustomAnimations(
            CompanionEntity entity,
            long instanceId,
            AnimationState<CompanionEntity> animationState
    ) {
        super.setCustomAnimations(entity, instanceId, animationState);

        for (GeoBone bone : this.getAnimationProcessor().getRegisteredBones()) {
            bone.setHidden(false);
            bone.setChildrenHidden(false);
        }

        for (GeoBone bone : this.getAnimationProcessor().getRegisteredBones()) {
            if (shouldHide(entity, bone.getName())) {
                bone.setHidden(true);
                bone.setChildrenHidden(true);
            }
        }
    }

    private static boolean shouldHide(CompanionEntity entity, String boneName) {
        String normalized = boneName.toLowerCase(Locale.ROOT);

        if (normalized.startsWith("armor")) {
            return entity.outfit()
                    != com.trolmastercard.sexmod.entity.OutfitMode.ARMORED;
        }

        if (COMMON_SCENE_BONES.contains(normalized)) {
            return !shouldShowSceneBone(entity, normalized);
        }

        if (ALEX_ARM_BONES.contains(normalized)) {
            return CompanionRenderer.partnerSkin(entity).model()
                    != net.minecraft.client.resources.PlayerSkin.Model.SLIM;
        }

        if (STEVE_ARM_BONES.contains(normalized)) {
            return CompanionRenderer.partnerSkin(entity).model()
                    == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
        }

        CompanionKind kind = entity.kind();
        int variant = entity.appearanceVariant();

        if (kind == CompanionKind.KOBOLD) {
            if (normalized.startsWith("freckles")) {
                return true;
            }

            if (normalized.matches("horn(?:ur|ul)[0-7]")) {
                int hornVariant = normalized.charAt(normalized.length() - 1) - '0';
                return hornVariant != Math.floorMod(variant, 8);
            }

            if (normalized.matches("horn(?:dr|dl)[1-2]")) {
                return true;
            }
        }

        if (kind == CompanionKind.BEE) {
            return normalized.equals("chest");
        }

        if (kind == CompanionKind.GOBLIN) {
            int selectedVariant = Math.floorMod(variant, 8);
            return switch (normalized) {
                case "variant7", "variant2" -> selectedVariant != 0;
                case "variant22", "variant6" -> selectedVariant != 1;
                case "variant56", "variant11" -> selectedVariant != 2;
                case "variant", "variant27" -> selectedVariant != 3;
                case "variant24", "variant29" -> selectedVariant != 4;
                case "as", "as2" -> selectedVariant != 5;
                case "bone12", "bone43" -> selectedVariant != 6;
                case "ghjjghj5", "ghjjghj8" -> selectedVariant != 7;
                case "variant25", "variant28" -> selectedVariant < 5;
                default -> normalized.equals("preggy");
            };
        }

        if (kind == CompanionKind.GALATH) {
            if (normalized.equals("futacock")
                    || normalized.equals("futaballll")
                    || normalized.equals("futaballlr")) {
                return Math.floorMod(variant, 2) == 0;
            }

            return normalized.equals("coin")
                    || normalized.equals("nipplel")
                    || normalized.equals("nippler");
        }

        if (kind == CompanionKind.MANGLELIE) {
            boolean noSkirtVariant = Math.floorMod(variant, 2) == 1;
            if (normalized.equals("cheeklbelowskirt")
                    || normalized.equals("cheekrbelowskirt")
                    || normalized.equals("sidelnoskirt")
                    || normalized.equals("sidernoskirt")) {
                return !noSkirtVariant;
            }

            if (normalized.equals("skirt")
                    || normalized.equals("sidelskirt")
                    || normalized.equals("siderskirt")) {
                return noSkirtVariant;
            }

            return normalized.equals("nipplel")
                    || normalized.equals("nippler");
        }

        return false;
    }

    private static boolean shouldShowSceneBone(CompanionEntity entity, String boneName) {
        var scene = entity.currentScene();
        if (scene == null) {
            return false;
        }

        if (boneName.equals("steve")) {
            return scene.hasPartner();
        }

        var phase = entity.currentSceneAnimation();
        return boneName.equals("cum")
                && phase != null
                && phase.animation().toLowerCase(Locale.ROOT).contains("cum");
    }
}
