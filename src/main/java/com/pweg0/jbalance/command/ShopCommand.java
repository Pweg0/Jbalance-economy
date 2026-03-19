package com.pweg0.jbalance.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.pweg0.jbalance.JBalance;
import com.pweg0.jbalance.service.ShopService;
import com.pweg0.jbalance.util.DiscordWebhook;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Shop navigation and management commands:
 * /setloja — create shop at current position
 * /delloja — delete own shop
 * /loja <player> — teleport to player's shop
 * /lojas — list all shops
 */
public class ShopCommand {

    private ShopCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /setloja
        dispatcher.register(
            Commands.literal("setloja")
                .requires(ShopCommand::canCreate)
                .executes(ShopCommand::setLoja)
        );

        // /delloja
        dispatcher.register(
            Commands.literal("delloja")
                .requires(ShopCommand::canCreate)
                .executes(ShopCommand::delLoja)
        );

        // /loja <player>
        dispatcher.register(
            Commands.literal("loja")
                .requires(ShopCommand::canTeleport)
                .then(Commands.argument("jogador", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        ShopService svc = ShopService.getInstance();
                        if (svc == null) return builder.buildFuture();
                        svc.listShops().thenAccept(shops -> {
                            for (var shop : shops) {
                                builder.suggest(shop.displayName());
                            }
                        });
                        return SharedSuggestionProvider.suggest(
                            ctx.getSource().getServer().getPlayerNames(), builder);
                    })
                    .executes(ShopCommand::teleportToShop))
        );

        // /lojas
        dispatcher.register(
            Commands.literal("lojas")
                .requires(ShopCommand::canTeleport)
                .executes(ShopCommand::listShops)
        );
    }

    // ── Permission checks ──

    private static boolean canCreate(CommandSourceStack src) {
        if (!src.isPlayer()) return true;
        try { return PermissionAPI.getPermission(src.getPlayerOrException(), JBalancePermissions.SHOP_CREATE); }
        catch (Exception e) { return true; }
    }

    private static boolean canTeleport(CommandSourceStack src) {
        if (!src.isPlayer()) return true;
        try { return PermissionAPI.getPermission(src.getPlayerOrException(), JBalancePermissions.SHOP_TELEPORT); }
        catch (Exception e) { return true; }
    }

    // ── /setloja ──

    private static int setLoja(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        UUID uuid = player.getUUID();
        String dimension = player.level().dimension().location().toString();

        ShopService.getInstance().createShop(uuid,
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(), dimension)
            .whenComplete((v, ex) -> src.getServer().execute(() -> {
                if (ex != null) {
                    JBalance.LOGGER.error("[JBalance] Failed to create shop for {}", uuid, ex);
                    src.sendFailure(Component.literal("\u00a76[JBalance] \u00a7cErro ao criar loja."));
                    return;
                }
                src.sendSuccess(() -> Component.literal(
                    "\u00a76[JBalance] \u00a7aLoja criada com sucesso! \u00a77Outros jogadores podem visitar com \u00a76/loja " + player.getName().getString()
                ), false);
                DiscordWebhook.logShopCreated(player.getName().getString(),
                    (int)player.getX(), (int)player.getY(), (int)player.getZ(), dimension);
            }));
        return Command.SINGLE_SUCCESS;
    }

    // ── /delloja ──

    private static int delLoja(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        UUID uuid = player.getUUID();

        ShopService.getInstance().deleteShop(uuid)
            .whenComplete((v, ex) -> src.getServer().execute(() -> {
                if (ex != null) {
                    src.sendFailure(Component.literal("\u00a76[JBalance] \u00a7cErro ao deletar loja."));
                    return;
                }
                src.sendSuccess(() -> Component.literal(
                    "\u00a76[JBalance] \u00a77Sua loja foi removida."
                ), false);
                DiscordWebhook.logShopDeleted(player.getName().getString());
            }));
        return Command.SINGLE_SUCCESS;
    }

    // ── /loja <player> ──

    private static int teleportToShop(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        String targetName = StringArgumentType.getString(ctx, "jogador");

        ShopService.getInstance().findShopByOwnerName(targetName)
            .thenCompose(ownerUuid -> {
                if (ownerUuid == null) return java.util.concurrent.CompletableFuture.completedFuture(null);
                return ShopService.getInstance().getShop(ownerUuid);
            })
            .whenComplete((shop, ex) -> src.getServer().execute(() -> {
                if (ex != null) {
                    src.sendFailure(Component.literal("\u00a76[JBalance] \u00a7cErro ao buscar loja."));
                    return;
                }
                if (shop == null) {
                    src.sendFailure(Component.literal(
                        "\u00a76[JBalance] \u00a7cJogador '" + targetName + "' nao possui loja."
                    ));
                    return;
                }
                // Resolve dimension
                ResourceKey<net.minecraft.world.level.Level> dimKey = ResourceKey.create(
                    Registries.DIMENSION, ResourceLocation.parse(shop.dimension()));
                ServerLevel targetLevel = src.getServer().getLevel(dimKey);
                if (targetLevel == null) {
                    src.sendFailure(Component.literal("\u00a76[JBalance] \u00a7cDimensao nao encontrada."));
                    return;
                }
                player.teleportTo(targetLevel, shop.x(), shop.y(), shop.z(), shop.yaw(), shop.pitch());
                src.sendSuccess(() -> Component.literal(
                    "\u00a76[JBalance] \u00a77Teleportado para a loja de \u00a76" + targetName
                ), false);
            }));
        return Command.SINGLE_SUCCESS;
    }

    // ── /lojas ──

    private static int listShops(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        ShopService.getInstance().listShops()
            .whenComplete((shops, ex) -> src.getServer().execute(() -> {
                if (ex != null) {
                    src.sendFailure(Component.literal("\u00a76[JBalance] \u00a7cErro ao listar lojas."));
                    return;
                }
                if (shops.isEmpty()) {
                    src.sendSuccess(() -> Component.literal(
                        "\u00a76[JBalance] \u00a77Nenhuma loja encontrada."
                    ), false);
                    return;
                }
                src.sendSuccess(() -> Component.literal(
                    "\u00a76[JBalance] \u00a77--- Lojas (" + shops.size() + ") ---"
                ), false);
                for (var shop : shops) {
                    Component shopLine = Component.literal(
                        "\u00a76" + shop.displayName() + " \u00a77[Visitar]"
                    ).withStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/loja " + shop.displayName()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Clique para teleportar para a loja de " + shop.displayName())))
                    );
                    src.sendSuccess(() -> shopLine, false);
                }
            }));
        return Command.SINGLE_SUCCESS;
    }
}
