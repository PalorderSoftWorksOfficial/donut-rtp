package io.nightbeam.donutrtp;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class RtpGuiScreenHandler extends GenericContainerScreenHandler {
    public enum MenuKind {
        MAIN,
        ADMIN
    }

    private final SimpleInventory inventory;
    private final MenuKind kind;

    public RtpGuiScreenHandler(int syncId, PlayerInventory playerInventory, MenuKind kind) {
        this(syncId, playerInventory, new SimpleInventory(27), kind);
    }

    private RtpGuiScreenHandler(int syncId, PlayerInventory playerInventory, SimpleInventory inventory, MenuKind kind) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, inventory, 3);
        this.inventory = inventory;
        this.kind = kind;
        populate();
    }

    public static void openMain(ServerPlayerEntity player) {
        SimpleNamedScreenHandlerFactory factory = new SimpleNamedScreenHandlerFactory((syncId, inventory, ignoredPlayer) -> new RtpGuiScreenHandler(syncId, inventory, MenuKind.MAIN), Text.literal("DonutRTP"));
        player.openHandledScreen(factory);
    }

    public static void openAdmin(ServerPlayerEntity player) {
        if (!player.isCreativeLevelTwoOp()) {
            player.sendMessage(Text.literal("You don't have permission."), true);
            return;
        }

        SimpleNamedScreenHandlerFactory factory = new SimpleNamedScreenHandlerFactory((syncId, inventory, ignoredPlayer) -> new RtpGuiScreenHandler(syncId, inventory, MenuKind.ADMIN), Text.literal("DonutRTP Admin"));
        player.openHandledScreen(factory);
    }

    private void populate() {
        inventory.clear();

        if (kind == MenuKind.MAIN) {
            inventory.setStack(10, named(Items.COMPASS, "Start RTP", Formatting.AQUA));
            inventory.setStack(12, named(Items.CLOCK, "Warmup: " + DonutRTPMod.CONFIG.warmupSeconds() + "s", Formatting.YELLOW));
            inventory.setStack(14, named(Items.CLOCK, "Cooldown: " + DonutRTPMod.CONFIG.cooldownSeconds() + "s", Formatting.GOLD));
            inventory.setStack(16, named(Items.BOOK, "Admin Menu", Formatting.LIGHT_PURPLE));
            inventory.setStack(22, named(Items.BARRIER, "Cancel Pending RTP", Formatting.RED));
            return;
        }

        inventory.setStack(10, named(Items.REDSTONE, "Radius -500", Formatting.RED));
        inventory.setStack(11, named(Items.EMERALD, "Radius +500", Formatting.GREEN));
        inventory.setStack(12, named(Items.PAPER, "Attempts -32", Formatting.RED));
        inventory.setStack(13, named(Items.PAPER, "Attempts +32", Formatting.GREEN));
        inventory.setStack(14, named(Items.FEATHER, "Per Tick -1", Formatting.RED));
        inventory.setStack(15, named(Items.FEATHER, "Per Tick +1", Formatting.GREEN));
        inventory.setStack(16, named(Items.CLOCK, "Warmup -1s", Formatting.RED));
        inventory.setStack(17, named(Items.CLOCK, "Warmup +1s", Formatting.GREEN));
        inventory.setStack(18, named(Items.HOPPER, "Cooldown -10s", Formatting.RED));
        inventory.setStack(19, named(Items.HOPPER, "Cooldown +10s", Formatting.GREEN));
        inventory.setStack(21, named(Items.BOOK, DonutRTPMod.SERVICE.statusLine(), Formatting.LIGHT_PURPLE));
        inventory.setStack(22, named(Items.ARROW, "Back", Formatting.YELLOW));
        inventory.setStack(24, named(Items.MAP, "Save Config", Formatting.AQUA));
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex >= 0 && slotIndex < inventory.size()) {
            handleClick(player, slotIndex);
            return;
        }

        super.onSlotClick(slotIndex, button, actionType, player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    private void handleClick(PlayerEntity player, int slotIndex) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        if (kind == MenuKind.MAIN) {
            switch (slotIndex) {
                case 10 -> DonutRTPMod.SERVICE.startTeleport(serverPlayer);
                case 16 -> openAdmin(serverPlayer);
                case 22 -> DonutRTPMod.SERVICE.cancelTeleport(serverPlayer, "Pending RTP cancelled.");
                default -> {
                }
            }
            return;
        }

        switch (slotIndex) {
            case 10 -> {
                DonutRTPMod.CONFIG.adjustRadius(-500);
                refresh(serverPlayer);
            }
            case 11 -> {
                DonutRTPMod.CONFIG.adjustRadius(500);
                refresh(serverPlayer);
            }
            case 12 -> {
                DonutRTPMod.CONFIG.adjustMaxAttempts(-32);
                refresh(serverPlayer);
            }
            case 13 -> {
                DonutRTPMod.CONFIG.adjustMaxAttempts(32);
                refresh(serverPlayer);
            }
            case 14 -> {
                DonutRTPMod.CONFIG.adjustAttemptsPerTick(-1);
                refresh(serverPlayer);
            }
            case 15 -> {
                DonutRTPMod.CONFIG.adjustAttemptsPerTick(1);
                refresh(serverPlayer);
            }
            case 16 -> {
                DonutRTPMod.CONFIG.adjustWarmupSeconds(-1);
                refresh(serverPlayer);
            }
            case 17 -> {
                DonutRTPMod.CONFIG.adjustWarmupSeconds(1);
                refresh(serverPlayer);
            }
            case 18 -> {
                DonutRTPMod.CONFIG.adjustCooldownSeconds(-10);
                refresh(serverPlayer);
            }
            case 19 -> {
                DonutRTPMod.CONFIG.adjustCooldownSeconds(10);
                refresh(serverPlayer);
            }
            case 22 -> openMain(serverPlayer);
            case 24 -> {
                DonutRTPMod.saveConfig();
                serverPlayer.sendMessage(Text.literal("DonutRTP configuration saved."), true);
                refresh(serverPlayer);
            }
            default -> {
            }
        }
    }

    private void refresh(ServerPlayerEntity player) {
        DonutRTPMod.saveConfig();
        if (kind == MenuKind.ADMIN) {
            openAdmin(player);
        } else {
            openMain(player);
        }
    }

    private static ItemStack named(net.minecraft.item.Item item, String name, Formatting formatting) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name).formatted(formatting));
        return stack;
    }
}
