package com.fartypoopy.sexmod.client;

import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.network.TribeCommandPayload;
import com.fartypoopy.sexmod.tribe.KoboldBehavior;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Client copy of the tribe state: the original's ff.aY (member list), j.d (follow flag) and gm (marked blocks). */
@EventBusSubscriber(modid = FapCraft.MOD_ID, value = Dist.CLIENT)
public final class TribeClient {
    private static final Set<BlockPos> MARKED = ConcurrentHashMap.newKeySet();
    private static volatile List<int[]> members = List.of();
    private static volatile boolean follow;
    /** Staff compass display style (original fa.o). */
    private static boolean compassList;
    private static int ticks;

    private TribeClient() {
    }

    public static boolean hasTribe() {
        return !members.isEmpty();
    }

    public static List<int[]> members() {
        return members;
    }

    public static boolean follow() {
        return follow;
    }

    public static void setUi(boolean followFlag, List<int[]> list) {
        follow = followFlag;
        members = List.copyOf(list);
    }

    public static boolean compassList() {
        return compassList;
    }

    public static void toggleCompass() {
        compassList = !compassList;
    }

    public static boolean isMarked(BlockPos pos) {
        return MARKED.contains(pos);
    }

    public static Set<BlockPos> marked() {
        return MARKED;
    }

    public static void markers(boolean add, List<BlockPos> blocks) {
        if (add) {
            MARKED.addAll(blocks);
        } else {
            MARKED.removeAll(blocks);
        }
    }

    public static void request() {
        PacketDistributor.sendToServer(TribeCommandPayload.of(TribeCommandPayload.REQUEST, BlockPos.ZERO));
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        // the original asked every second; only needed while a staff is held (claiming and the menu require it)
        if (++ticks >= 20) {
            ticks = 0;
            if (KoboldBehavior.holdsStaff(minecraft.player)) {
                request();
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        MARKED.clear();
        members = List.of();
        follow = false;
    }
}
