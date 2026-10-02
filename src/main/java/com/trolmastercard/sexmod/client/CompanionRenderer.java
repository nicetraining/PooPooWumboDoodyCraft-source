package com.trolmastercard.sexmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.trolmastercard.sexmod.entity.CompanionEntity;
import com.trolmastercard.sexmod.entity.CompanionKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class CompanionRenderer extends GeoEntityRenderer<CompanionEntity> {
    private static final java.util.Map<Integer, GeoBone> BOY_CAMS = new java.util.concurrent.ConcurrentHashMap<>();

    /** The animated {@code boyCam} bone as of the last frame this entity was drawn, or null. */
    public static GeoBone boyCam(int entityId) {
        return BOY_CAMS.get(entityId);
    }

    public CompanionRenderer(EntityRendererProvider.Context context) {
        super(context, new CompanionModel());
        this.shadowRadius = 0.2F;
        this.addRenderLayer(new FishingItemLayer(this));
        this.addRenderLayer(new SkirtLayer(this));
        this.addRenderLayer(new ClothLayer(this));
        this.addRenderLayer(new GalathMeshLayer(this));
    }

    @Override
    public void render(CompanionEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        var scene = entity.currentScene();
        if (scene != null && scene.hidesEntity()) {
            return;
        }
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public void preRender(
            PoseStack poseStack,
            CompanionEntity entity,
            BakedGeoModel model,
            MultiBufferSource bufferSource,
            VertexConsumer buffer,
            boolean isReRender,
            float partialTick,
            int packedLight,
            int packedOverlay,
            int renderColor
    ) {
        super.preRender(
                poseStack,
                entity,
                model,
                bufferSource,
                buffer,
                isReRender,
                partialTick,
                packedLight,
                packedOverlay,
                renderColor
        );

        float baseScale = entity.kind() == CompanionKind.KOBOLD ? 0.75F + entity.koboldSize() : 1.0F;
        if (entity.goblin() != null) {
            // original dy.a(): a caught goblin is drawn at half size; a vanishing robber shrinks with its fade
            baseScale = (entity.goblinState() == com.trolmastercard.sexmod.entity.GoblinBehavior.CATCH ? 0.5F : 1.0F) * entity.goblinFade();
        }
        float scale = baseScale * entity.appearanceScale();
        if (entity.allieFadeStart >= 0L && entity.level() != null) {
            // original d8: while she fades out she shrinks to U and rises by 3 - 3U
            float u = Math.max(0.0F, 1.0F - (entity.level().getGameTime() + partialTick - entity.allieFadeStart) / 33.0F);
            poseStack.translate(0.0F, 3.0F - 3.0F * u, 0.0F);
            scale *= u;
        }
        poseStack.scale(scale, scale, scale);
    }

    @Override
    public void renderRecursively(
            PoseStack poseStack,
            CompanionEntity entity,
            GeoBone bone,
            RenderType renderType,
            MultiBufferSource bufferSource,
            VertexConsumer buffer,
            boolean isReRender,
            float partialTick,
            int packedLight,
            int packedOverlay,
            int renderColor
    ) {
        if (bone.getName().equals("Head2")) {
            // The original skips the partner's head while the local player is that partner in first
            // person; the camera sits inside it otherwise. Children (the camera bone) still update.
            Minecraft minecraft = Minecraft.getInstance();
            boolean localPartner = minecraft.player != null
                    && entity.currentScene() != null
                    && entity.currentScene().hasPartner()
                    && entity.scenePartnerId().filter(minecraft.player.getUUID()::equals).isPresent();
            bone.setHidden(localPartner && minecraft.options.getCameraType().isFirstPerson());
            bone.setChildrenHidden(false); // setHidden also hides children, which would freeze the camera bone
        }
        if (GalathMeshLayer.tracks(bone.getName())) {
            bone.setTrackingMatrices(true);
        }
        if (bone.getName().startsWith("skirt_") || bone.getName().startsWith("clothBoob")) {
            bone.setTrackingMatrices(true); // SkirtLayer joins these bones with quads
        }
        if (bone.getName().equals("boyCam")) {
            bone.setTrackingMatrices(true);
            BOY_CAMS.put(entity.getId(), bone);
        }
        if (bone.getName().equalsIgnoreCase("steve")
                && entity.currentScene() != null
                && entity.currentScene().hasPartner()) {
            PlayerSkin skin = partnerSkin(entity);
            renderType = RenderType.entityTranslucent(skin.texture());
            buffer = bufferSource.getBuffer(renderType);
        }

        if (entity.currentScene() != null && entity.currentScene().hasPartner() && isPartnerProp(bone)) {
            // original d_: the skin gets two painted patches (0,0,4,3 skin tone; 4,0,3,3 pink) that this mesh samples
            renderType = RenderType.entityCutoutNoCull(propTexture());
            buffer = bufferSource.getBuffer(renderType);
        }
        if (bone.getName().equals("body2") && entity.currentScene() != null
                && entity.currentScene().modelOverride() != null) {
            // original da: in the combined model the daughter's half (body2) is drawn with her own texture
            renderType = RenderType.entityCutoutNoCull(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    "sexmod", "textures/entity/manglelie/manglelie.png"));
            buffer = bufferSource.getBuffer(renderType);
        }
        if (entity.allieFadeStart >= 0L && entity.level() != null) {
            float fade = 1.0F - (entity.level().getGameTime() + partialTick - entity.allieFadeStart) / 33.0F;
            if (fade <= 0.0F) {
                return;
            }
            renderType = RenderType.entityTranslucent(this.getTextureLocation(entity));
            buffer = bufferSource.getBuffer(renderType);
            renderColor = (Math.min(255, (int) (fade * 255.0F)) << 24) | (renderColor & 0xFFFFFF);
        }

        if (entity.kind() == CompanionKind.GALATH && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            // original f_.v(): in first person she is half transparent while she fights with the sword or is knocked out
            String animation = GalathMeshLayer.playing(entity);
            if (animation.equals("animation.galath.attack") || animation.equals("animation.galath.knockout_air")
                    || animation.equals("animation.galath.knocked_out")) {
                renderType = RenderType.entityTranslucent(this.getTextureLocation(entity));
                buffer = bufferSource.getBuffer(renderType);
                renderColor = 0x80000000 | (renderColor & 0xFFFFFF);
            }
        }
        if (entity.kind() == CompanionKind.KOBOLD) {
            renderColor = koboldTint(entity, bone.getName(), renderColor);
        }
        if (entity.kind() == CompanionKind.GALATH && bone.getName().equals("pentagram")
                && "animation.galath.give_coin".equals(GalathMeshLayer.playing(entity))) {
            // original da: the pentagram turns from grey to pink and glows brighter between ticks 105 and 125 of the coin scene
            float glow = (net.minecraft.util.Mth.clamp(GalathMeshLayer.elapsed(entity, "animation.galath.give_coin", partialTick),
                    105.0F, 125.0F) - 105.0F) / 20.0F;
            int r = (int) (255.0F * net.minecraft.util.Mth.lerp(glow, 0.44705883F, 0.84705883F));
            int g = (int) (255.0F * net.minecraft.util.Mth.lerp(glow, 0.44705883F, 0.11764706F));
            int b = (int) (255.0F * net.minecraft.util.Mth.lerp(glow, 0.44705883F, 0.35686275F));
            renderColor = 0xFF000000 | (r << 16) | (g << 8) | b;
            int level = (int) (net.minecraft.util.Mth.lerp(glow, 120.0F, 240.0F) / 16.0F);
            packedLight = net.minecraft.client.renderer.LightTexture.pack(level, level);
        }
        net.minecraft.world.entity.EquipmentSlot armourSlot = CompanionModel.armourSlot(bone.getName().toLowerCase(java.util.Locale.ROOT));
        if (armourSlot != null && (entity.kind() == CompanionKind.JENNY || entity.kind() == CompanionKind.ELLIE
                || entity.kind() == CompanionKind.BIA || entity.kind() == CompanionKind.LUNA)
                && entity.getItemBySlot(armourSlot).getItem() instanceof net.minecraft.world.item.ArmorItem armour) {
            // original d_.a(String, r, g, b): the material picks a 72 pixel wide strip of the texture, leather is dyed
            var material = armour.getMaterial();
            int strip = 0;
            if (material.is(net.minecraft.world.item.ArmorMaterials.LEATHER)) {
                strip = 4;
                int dye = net.minecraft.world.item.component.DyedItemColor.getOrDefault(entity.getItemBySlot(armourSlot), -6265536);
                renderColor = (renderColor & 0xFF000000) | (dye & 0xFFFFFF);
            } else if (material.is(net.minecraft.world.item.ArmorMaterials.CHAIN) || material.is(net.minecraft.world.item.ArmorMaterials.IRON)) {
                strip = 2;
            } else if (material.is(net.minecraft.world.item.ArmorMaterials.GOLD)) {
                strip = 1;
            }
            if (strip != 0) {
                buffer = new UvShift(buffer, 72.0F * strip / 4096.0F);
            }
        }
        if (entity.goblin() != null) {
            renderColor = goblinLook(entity, bone, renderColor);
        }

        super.renderRecursively(
                poseStack,
                entity,
                bone,
                renderType,
                bufferSource,
                buffer,
                isReRender,
                partialTick,
                packedLight,
                packedOverlay,
                renderColor
        );
    }

    private static final java.util.Map<String, int[]> GOBLIN_FIELDS = new java.util.HashMap<>();
    private static final java.util.Set<String> GOBLIN_SKIN = java.util.Set.of("meatTorso", "meatCheekR", "meatCheekL", "meatFootR",
            "meatFootL", "meatShinR", "meatShinL", "meatLegL", "meatLegR", "nippleR", "nippleL", "preggy", "shoeL", "shoeR",
            "frontAndInside", "Lside", "Rside", "cheekR", "cheekL", "fuckhole", "head", "nose", "neck", "armL", "lowerArmL",
            "armR", "lowerArmR", "torso", "LegL", "LegR", "shinL", "shinR");
    private static final java.util.Set<String> GOBLIN_LASHES = java.util.Set.of("lashR", "lashL", "closedR", "closedL", "browL", "browR");

    /** Original dy: ears, hair and crown pick their bones from the model string; every bone is tinted by name. */
    private static int goblinLook(CompanionEntity entity, GeoBone bone, int renderColor) {
        int[] fields = GOBLIN_FIELDS.computeIfAbsent(entity.goblinModel(), com.trolmastercard.sexmod.entity.GoblinModel::parse);
        String name = bone.getName();
        if (fields.length >= 10) {
            switch (name) {
                case "earL" -> goblinEar(bone, fields[0], fields[1], fields[3]);
                case "earR" -> goblinEar(bone, fields[0], fields[2], fields[4]);
                case "hair" -> goblinSelect(bone, fields[5]);
                default -> {
                }
            }
            if (name.contains("crown")) {
                bone.setHidden(fields[9] == 0);
            }
        }
        int rgb = com.trolmastercard.sexmod.entity.GoblinModel.tint(name, fields, GOBLIN_SKIN, GOBLIN_LASHES);
        return rgb < 0 ? renderColor : (renderColor & 0xFF000000) | rgb;
    }

    /** Original d6.a(bone, index): children sorted by pivot height, only the chosen one is shown. */
    private static GeoBone goblinSelect(GeoBone parent, int index) {
        java.util.List<GeoBone> sorted = new java.util.ArrayList<>(parent.getChildBones());
        sorted.sort(java.util.Comparator.comparingDouble(GeoBone::getPivotY));
        GeoBone chosen = null;
        for (int i = 0; i < sorted.size(); i++) {
            boolean show = i == index;
            sorted.get(i).setHidden(!show);
            if (show) {
                chosen = sorted.get(i);
            }
        }
        return chosen;
    }

    private static void goblinEar(GeoBone ear, int group, int sub, int accessories) {
        GeoBone groupBone = goblinSelect(ear, group);
        GeoBone subBone = groupBone == null ? null : goblinSelect(groupBone, sub);
        if (subBone == null) {
            return;
        }
        java.util.List<GeoBone> children = subBone.getChildBones();
        for (GeoBone child : children) {
            child.setHidden(true);
        }
        for (int index : com.trolmastercard.sexmod.entity.GoblinModel.subset(children.size(), accessories)) {
            if (index < children.size()) {
                children.get(index).setHidden(false);
            }
        }
    }

    private static final java.util.Set<String> BODY_BONES = java.util.Set.of("colorSpots", "neck", "head", "snout", "midSectionR",
            "midSectionL", "innerCheekLR", "innerCheekRR", "gayL", "gayR", "legR", "legL", "shinL", "toesL", "kneeL", "curvesL",
            "shinR", "toesR", "kneeR", "curvesR", "sideL", "sideR", "hip", "torsoL", "torsoR", "armR", "lowerArmR", "ellbowR",
            "armL", "lowerArmL", "ellbowL", "hornUL", "hornUR", "tail", "tail2", "tail3", "tail4", "tail5", "hornDL2", "hornDR2",
            "hornDR3M", "hornDL3M", "frecklesAL1", "frecklesAL2", "frecklesAR1", "frecklesAR2", "frecklesHL1", "frecklesHL2",
            "frecklesHR1", "frecklesHR2");
    private static final java.util.Set<String> SECONDARY_BONES = java.util.Set.of("boobR", "boobL", "frontNeck", "Rside", "Lside",
            "frontAndInside", "innerCheekLL", "innerCheekRL", "layer", "layer2", "down", "down2", "down3", "down4", "down5",
            "fuckhole", "hornDR3S", "hornDL3S", "assholeCoverUp", "assholeCoverUp2");

    /** Original de.a(String): body bones use the body colour, secondary bones its secondary, irises the eye colour. */
    private static int koboldTint(CompanionEntity entity, String bone, int renderColor) {
        int alpha = renderColor & 0xFF000000;
        if (BODY_BONES.contains(bone)) {
            return alpha | entity.bodyColor().mainRgb();
        }
        if (SECONDARY_BONES.contains(bone)) {
            return alpha | entity.bodyColor().secondaryRgb();
        }
        if (bone.equals("irisR") || bone.equals("irisL")) {
            return alpha | entity.eyeColor().mainRgb();
        }
        return alpha | 0xFFFFFF;
    }

    /** Original dj: tribe members show their coloured tribe name over their head. */
    @Override
    public boolean shouldShowName(CompanionEntity entity) {
        if (entity.kind() == CompanionKind.KOBOLD && !"null".equals(entity.tribeName())) {
            return Minecraft.getInstance().getEntityRenderDispatcher().distanceToSqr(entity) < 64.0D * 64.0D;
        }
        return super.shouldShowName(entity);
    }

    @Override
    protected void renderNameTag(CompanionEntity entity, net.minecraft.network.chat.Component displayName, PoseStack poseStack,
                                 MultiBufferSource bufferSource, int packedLight, float partialTick) {
        if (entity.kind() == CompanionKind.KOBOLD && !"null".equals(entity.tribeName())) {
            net.minecraft.network.chat.MutableComponent tag = entity.hasCustomName()
                    ? entity.getCustomName().copy().append(" ") : net.minecraft.network.chat.Component.literal("");
            displayName = tag.append(net.minecraft.network.chat.Component.literal("-" + entity.tribeName() + "-")
                    .withStyle(entity.bodyColor().chat()));
        }
        super.renderNameTag(entity, displayName, poseStack, bufferSource, packedLight, partialTick);
    }

    private static net.minecraft.resources.ResourceLocation propTextureLocation;

    private static boolean isPartnerProp(GeoBone bone) {
        for (GeoBone current = bone; current != null; current = current.getParent()) {
            String name = current.getName();
            if (name.equals("cock") || name.equals("ballL") || name.equals("ballR")) {
                return true;
            }
            if (name.equals("steve")) {
                return false;
            }
        }
        return false;
    }

    private static net.minecraft.resources.ResourceLocation propTexture() {
        if (propTextureLocation == null) {
            com.mojang.blaze3d.platform.NativeImage image = new com.mojang.blaze3d.platform.NativeImage(64, 64, true);
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 7; x++) {
                    // NativeImage pixels are ABGR
                    image.setPixelRGBA(x, y, x < 4 ? 0xFFA5C7F5 : 0xFFA99DF5);
                }
            }
            propTextureLocation = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    com.trolmastercard.sexmod.FapCraft.MOD_ID, "dynamic/partner_prop");
            Minecraft.getInstance().getTextureManager().register(
                    propTextureLocation, new net.minecraft.client.renderer.texture.DynamicTexture(image));
        }
        return propTextureLocation;
    }

    static PlayerSkin partnerSkin(CompanionEntity entity) {
        var partnerId = entity.scenePartnerId().orElse(entity.getUUID());
        var connection = Minecraft.getInstance().getConnection();
        PlayerInfo playerInfo = connection == null ? null : connection.getPlayerInfo(partnerId);
        return playerInfo == null ? DefaultPlayerSkin.get(partnerId) : playerInfo.getSkin();
    }

    /**
     * Original d_ item rendering on the "weapon" bone, for now only Luna's fishing items: her rod while she starts /
     * idles at the water (cast or not) and the caught item while she eats or throws it away.
     */
    private static final class FishingItemLayer extends software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer<CompanionEntity> {
        FishingItemLayer(software.bernie.geckolib.renderer.GeoRenderer<CompanionEntity> renderer) {
            super(renderer);
        }

        @Override
        protected net.minecraft.world.item.ItemStack getStackForBone(GeoBone bone, CompanionEntity entity) {
            if ("itemRenderer".equals(bone.getName())) {
                var scene = entity.currentScene();
                return scene != null && scene.isPayment() ? entity.paymentItem() : net.minecraft.world.item.ItemStack.EMPTY;
            }
            if (!"weapon".equals(bone.getName())) {
                return net.minecraft.world.item.ItemStack.EMPTY;
            }
            if (entity.kind() != CompanionKind.LUNA) {
                if (entity.kind() != CompanionKind.JENNY && entity.kind() != CompanionKind.ELLIE
                        && entity.kind() != CompanionKind.BIA) {
                    return net.minecraft.world.item.ItemStack.EMPTY;
                }
                // original d_ item hook: the girl's real weapon (slot 0) or bow (slot 1), whatever the player equipped
                return switch (entity.action()) {
                    case ATTACK -> entity.getMainHandItem();
                    case BOW -> entity.getOffhandItem();
                    default -> net.minecraft.world.item.ItemStack.EMPTY;
                };
            }
            if (entity.action() == com.trolmastercard.sexmod.entity.CompanionAction.ATTACK) {
                return entity.getMainHandItem();
            }
            return switch (entity.fishState()) {
                case com.trolmastercard.sexmod.entity.LunaFishing.START, com.trolmastercard.sexmod.entity.LunaFishing.IDLE ->
                        new net.minecraft.world.item.ItemStack(com.trolmastercard.sexmod.registry.ModItems.LUNA_ROD.get());
                case com.trolmastercard.sexmod.entity.LunaFishing.EAT, com.trolmastercard.sexmod.entity.LunaFishing.THROW_AWAY ->
                        entity.fishCaught();
                default -> net.minecraft.world.item.ItemStack.EMPTY;
            };
        }

        @Override
        protected net.minecraft.world.item.ItemDisplayContext getTransformTypeForStack(
                GeoBone bone, net.minecraft.world.item.ItemStack stack, CompanionEntity entity) {
            return net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        }
    }

    /** Adds a fixed offset to the U coordinate of every vertex (the armour material strips of the girls' textures). */
    private static final class UvShift implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float offset;

        private UvShift(VertexConsumer delegate, float offset) {
            this.delegate = delegate;
            this.offset = offset;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            this.delegate.setColor(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.delegate.setUv(u + this.offset, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            this.delegate.setNormal(x, y, z);
            return this;
        }
    }
}
