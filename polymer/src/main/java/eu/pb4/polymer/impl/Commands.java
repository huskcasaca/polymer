package eu.pb4.polymer.impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import eu.pb4.polymer.api.item.PolymerItemGroup;
import eu.pb4.polymer.api.item.PolymerItemUtils;
import eu.pb4.polymer.api.networking.PolymerSyncUtils;
import eu.pb4.polymer.api.other.PolymerStat;
import eu.pb4.polymer.api.resourcepack.PolymerRPUtils;
import eu.pb4.polymer.api.utils.PolymerObject;
import eu.pb4.polymer.api.utils.PolymerUtils;
import eu.pb4.polymer.api.x.BlockMapper;
import eu.pb4.polymer.impl.interfaces.PolymerNetworkHandlerExtension;
import eu.pb4.polymer.impl.networking.PolymerServerProtocol;
import eu.pb4.polymer.impl.ui.CreativeTabListUi;
import eu.pb4.polymer.impl.ui.CreativeTabUi;
import eu.pb4.polymer.impl.ui.PotionUi;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.nbt.visitor.NbtTextFormatter;
import net.minecraft.screen.LecternScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.state.property.Property;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.registry.Registry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

@SuppressWarnings("ResultOfMethodCallIgnored")
@ApiStatus.Internal
public class Commands {
    private static final Text[] ABOUT_PLAYER;
    private static final Text[] ABOUT_COLORLESS;
    private static volatile boolean alreadyGeneration = false;

    static {
        var about = new ArrayList<Text>();
        var aboutBasic = new ArrayList<Text>();
        var output = new ArrayList<Text>();

        try {
            about.add(Text.literal("Polymer").setStyle(Style.EMPTY.withColor(0xb4ff90).withBold(true).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, PolymerImpl.GITHUB_URL))));
            about.add(Text.literal("Version: ").setStyle(Style.EMPTY.withColor(0xf7e1a7))
                    .append(Text.literal(PolymerImpl.VERSION).setStyle(Style.EMPTY.withColor(Formatting.WHITE))));

            aboutBasic.addAll(about);
            aboutBasic.add(Text.empty());
            aboutBasic.add(Text.of(PolymerImpl.DESCRIPTION));

            about.add(Text.literal("")
                    .append(Text.literal("Contributors")
                            .setStyle(Style.EMPTY.withColor(Formatting.AQUA)
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                            Text.literal(String.join("\n", PolymerImpl.CONTRIBUTORS))
                                    ))
                            ))
                    .append("")
                    .setStyle(Style.EMPTY.withColor(Formatting.DARK_GRAY)));
            about.add(Text.empty());

            var desc = new ArrayList<>(List.of(PolymerImpl.DESCRIPTION.split(" ")));

            if (desc.size() > 0) {
                StringBuilder descPart = new StringBuilder();
                while (!desc.isEmpty()) {
                    (descPart.isEmpty() ? descPart : descPart.append(" ")).append(desc.remove(0));

                    if (descPart.length() > 16) {
                        about.add(Text.literal(descPart.toString()).setStyle(Style.EMPTY.withColor(Formatting.GRAY)));
                        descPart = new StringBuilder();
                    }
                }

                if (descPart.length() > 0) {
                    about.add(Text.literal(descPart.toString()).setStyle(Style.EMPTY.withColor(Formatting.GRAY)));
                }
            }

            if (PolymerImplUtils.ICON.length > about.size() + 2) {
                int a = 0;
                for (int i = 0; i < PolymerImplUtils.ICON.length; i++) {
                    if (i == (PolymerImplUtils.ICON.length - about.size() - 1) / 2 + a && a < about.size()) {
                        output.add(PolymerImplUtils.ICON[i].copy().append("  ").append(about.get(a++)));
                    } else {
                        output.add(PolymerImplUtils.ICON[i]);
                    }
                }
            } else {
                Collections.addAll(output, PolymerImplUtils.ICON);
                output.addAll(about);
            }
        } catch (Exception e) {
            e.printStackTrace();
            var invalid = Text.literal("/!\\ [ Invalid about mod info ] /!\\").setStyle(Style.EMPTY.withColor(0xFF0000).withItalic(true));

            output.add(invalid);
            about.add(invalid);
        }

        ABOUT_PLAYER = output.toArray(new Text[0]);
        ABOUT_COLORLESS = aboutBasic.toArray(new Text[0]);
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var command = literal("polymer")
                .requires(PolymerImplUtils.permission("command.core", PolymerImpl.CORE_COMMAND_MINIMAL_OP))
                .executes(Commands::about)
                .then(literal("generate-pack")
                        .requires(PolymerImplUtils.permission("command.generate", 3))
                        .executes(Commands::generateResources))
                .then(literal("stats")
                        .requires(PolymerImplUtils.permission("command.stats", 0))
                        .executes(Commands::stats)
                )
                .then(literal("effects")
                        .requires(PolymerImplUtils.permission("command.effects", 0))
                        .executes(Commands::effects)
                )
                .then(literal("client-item")
                        .requires(PolymerImplUtils.permission("command.client-item", 3))
                        .executes(Commands::displayClientItem)
                        .then(literal("get").executes(Commands::getClientItem))
                )
                .then(literal("export-registry")
                        .requires(PolymerImplUtils.permission("command.export-registry", 3))
                        .executes(Commands::dumpRegistries)
                )
                .then(literal("target-block")
                        .requires(PolymerImplUtils.permission("command.target-block", 3))
                        .executes(Commands::targetBlock)
                )
                .then(literal("creative")
                        .requires(PolymerImplUtils.permission("command.creative", 0))
                        .then(argument("itemGroup", IdentifierArgumentType.identifier())
                                .suggests((context, builder) -> {
                                    var remaining = builder.getRemaining().toLowerCase(Locale.ROOT);

                                    var groups = PolymerUtils.getItemGroups(context.getSource().getPlayer());

                                    CommandSource.forEachMatching(groups, remaining, PolymerItemGroup::getId, group -> builder.suggest(group.getId().toString(), group.getDisplayName()));
                                    return builder.buildFuture();
                                })
                                .executes(Commands::creativeTab)
                        )
                        .executes(Commands::creativeTab));

        if (PolymerImpl.DEVELOPER_MODE) {
            command.then(literal("dev")
                    .requires(PolymerImplUtils.permission("command.dev", 3))
                    .then(literal("reload-world")
                            .executes((ctx) -> {
                                PolymerUtils.reloadWorld(ctx.getSource().getPlayer());
                                return 0;
                            })
                    )
                    .then(literal("get-mapper")
                            .executes((ctx) -> {
                                ctx.getSource().sendFeedback(Text.literal(BlockMapper.getFrom(ctx.getSource().getPlayer()).getMapperName()), false);
                                return 0;
                            })
                    )
                    .then(literal("reset-mapper")
                            .executes((ctx) -> {
                                BlockMapper.resetMapper(ctx.getSource().getPlayer());
                                return 0;
                            })
                    )
                    .then(literal("run-sync")
                            .executes((ctx) -> {
                                PolymerSyncUtils.synchronizePolymerRegistries(ctx.getSource().getPlayer().networkHandler);
                                return 0;
                            }))
                    .then(literal("protocol-info")
                            .executes((ctx) -> {
                                ctx.getSource().sendFeedback(Text.literal("Protocol supported by your client:"), false);
                                for (var entry : PolymerNetworkHandlerExtension.of(ctx.getSource().getPlayer().networkHandler).polymer_getSupportMap().object2IntEntrySet()) {
                                    ctx.getSource().sendFeedback(Text.literal("- " + entry.getKey() + " = " + entry.getIntValue()), false);
                                }
                                return 0;
                            })
                    ).then(literal("validate_states")
                            .executes((ctx) -> {
                                PolymerServerProtocol.sendDebugValidateStatesPackets(ctx.getSource().getPlayer().networkHandler);
                                return 0;
                            })
                    )
                    .then(literal("set-pack-status")
                            .then(argument("status", BoolArgumentType.bool())
                                    .executes((ctx) -> {
                                        var status = ctx.getArgument("status", Boolean.class);
                                        PolymerRPUtils.setPlayerStatus(ctx.getSource().getPlayer(), status);
                                        ctx.getSource().sendFeedback(Text.literal("New resource pack status: " + status), false);
                                        return 0;
                                    }))
                    )
                    .then(literal("get-pack-status")
                            .executes((ctx) -> {
                                var status = PolymerRPUtils.hasPack(ctx.getSource().getPlayer());
                                ctx.getSource().sendFeedback(Text.literal("Resource pack status: " + status), false);
                                return 0;
                            })
                    )
            );
        }

        dispatcher.register(command);
    }

    private static int targetBlock(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var raycast = (BlockHitResult) context.getSource().getPlayer().raycast(10, 0, true);

        var builder = new StringBuilder();
        var state = context.getSource().getWorld().getBlockState(raycast.getBlockPos());

        builder.append(Registry.BLOCK.getId(state.getBlock()));

        if (!state.getBlock().getStateManager().getProperties().isEmpty()) {
            builder.append("[");
            var iterator = state.getBlock().getStateManager().getProperties().iterator();

            while (iterator.hasNext()) {
                var property = iterator.next();
                builder.append(property.getName());
                builder.append("=");
                builder.append(((Property) property).name(state.get(property)));

                if (iterator.hasNext()) {
                    builder.append(",");
                }
            }
            builder.append("]");
        }

        context.getSource().sendFeedback(Text.literal(builder.toString()), false);

        return 0;
    }

    private static int dumpRegistries(CommandContext<ServerCommandSource> context) {
        var path = PolymerImplUtils.dumpRegistry();
        if (path != null) {
            context.getSource().sendFeedback(Text.literal("Exported registry state as " + path), false);
        } else {
            context.getSource().sendError(Text.literal("Couldn't export registry!"));
        }
        return 0;
    }

    private static int effects(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        new PotionUi(context.getSource().getPlayer());
        return 1;
    }

    private static int stats(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayer();

        var list = new NbtList();

        int line = 0;
        MutableText text = null;

        for (var statId : Registry.CUSTOM_STAT) {
            if (statId instanceof PolymerObject) {
                var stat = Stats.CUSTOM.getOrCreateStat(statId);

                if (text == null) {
                    text = Text.literal("");
                }

                var statVal = player.getStatHandler().getStat(stat);

                text.append(PolymerStat.getName(statId)).append(Text.literal(": ").formatted(Formatting.GRAY)).append(Text.literal(stat.format(statVal) + "\n").formatted(Formatting.DARK_GRAY));
                line++;

                if (line == 13) {
                    list.add(NbtString.of(Text.Serializer.toJson(text)));
                    text = null;
                    line = 0;
                }
            }
        }

        if (text != null) {
            list.add(NbtString.of(Text.Serializer.toJson(text)));
        }

        var stack = new ItemStack(Items.WRITTEN_BOOK);
        stack.getOrCreateNbt().put("pages", list);
        stack.getOrCreateNbt().putString("title", "/polymer starts");
        stack.getOrCreateNbt().putString("author", player.getGameProfile().getName());


        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.empty();
            }

            @Nullable
            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
                var lectern = new LecternScreenHandler(syncId) {
                    @Override
                    public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
                        return false;
                    }

                    @Override
                    public boolean canInsertIntoSlot(Slot slot) {
                        return false;
                    }

                    @Override
                    public boolean onButtonClick(PlayerEntity player, int id) {
                        if (id == 3) {
                            return false;
                        } else {
                            return super.onButtonClick(player, id);
                        }
                    }

                    @Override
                    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
                        // noop
                    }
                };
                lectern.getSlot(0).setStack(stack);
                return lectern;
            }
        });

        return 1;
    }

    private static int about(CommandContext<ServerCommandSource> context) {
        for (var text : (context.getSource().getEntity() instanceof ServerPlayerEntity ? ABOUT_PLAYER : ABOUT_COLORLESS)) {
            context.getSource().sendFeedback(text, false);
        }

        return 0;
    }

    private static int creativeTab(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        if (context.getSource().getPlayer().isCreative()) {
            try {
                var itemGroup = PolymerItemGroup.REGISTRY.get(context.getArgument("itemGroup", Identifier.class));
                if (itemGroup != null && itemGroup.shouldSyncWithPolymerClient(context.getSource().getPlayer())) {
                    new CreativeTabUi(context.getSource().getPlayer(), itemGroup);
                    return 2;
                }
            } catch (Exception e) {
                //
            }

            new CreativeTabListUi(context.getSource().getPlayer());
            return 1;
        } else {
            return 0;
        }
    }

    private static int displayClientItem(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayer();
        var stack = PolymerItemUtils.getPolymerItemStack(player.getMainHandStack(), player);
        stack.getOrCreateNbt().remove(PolymerItemUtils.POLYMER_ITEM_ID);
        stack.getOrCreateNbt().remove(PolymerItemUtils.REAL_TAG);

        context.getSource().sendFeedback((new NbtTextFormatter("", 3)).apply(stack.writeNbt(new NbtCompound())), false);

        return 1;
    }

    private static int getClientItem(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayer();

        var stack = PolymerItemUtils.getPolymerItemStack(player.getMainHandStack(), player);
        stack.getOrCreateNbt().remove(PolymerItemUtils.POLYMER_ITEM_ID);
        stack.getOrCreateNbt().remove(PolymerItemUtils.REAL_TAG);
        player.giveItemStack(stack.copy());
        context.getSource().sendFeedback(Text.literal("Given client representation to player"), true);

        return 1;
    }

    public static int generateResources(CommandContext<ServerCommandSource> context) {
        if (alreadyGeneration) {
            context.getSource().sendFeedback(Text.literal("Pack is already generating! Wait for it to finish..."), true);
        } else {
            alreadyGeneration = true;

            Util.getIoWorkerExecutor().execute(() -> {
                context.getSource().sendFeedback(Text.literal("Starting resource pack generation..."), true);
                boolean success = PolymerRPUtils.build();

                context.getSource().getServer().execute(() -> {
                    if (success) {
                        context.getSource().sendFeedback(
                                Text.literal("Resource pack created successfully! You can find it in game folder as ")
                                .append(Text.literal("polymer-resourcepack.zip")
                                        .setStyle(Style.EMPTY.withUnderline(true).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(PolymerRPUtils.DEFAULT_PATH.toAbsolutePath().toString()))))),
                                true
                        );
                    } else {
                        context.getSource().sendError(Text.literal("Found issues while creating resource pack! See logs above for more detail!"));
                    }
                });

                alreadyGeneration = false;
            });
        }
        return 0;
    }
}
