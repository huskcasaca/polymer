package eu.pb4.polymer.impl;

import eu.pb4.polymer.api.utils.PolymerUtils;
import eu.pb4.polymer.impl.compat.CompatStatus;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.function.Predicate;

public class PolymerImplUtils {
    public static final Text[] ICON;
    public static ThreadLocal<ServerPlayerEntity> playerTargetHack = new ThreadLocal<>();
    private static ItemStack NO_TEXTURE;

    public static void setPlayer(ServerPlayerEntity player) {
        playerTargetHack.set(player);
    }

    @Nullable
    public static ServerPlayerEntity getPlayer() {
        return playerTargetHack.get();
    }


    public static Predicate<ServerCommandSource> permission(String path, int operatorLevel) {
        if (CompatStatus.FABRIC_PERMISSION_API_V0) {
            return Permissions.require("polymer." + path, operatorLevel);
        } else {
            return source -> source.hasPermissionLevel(operatorLevel);
        }
    }

    public static Identifier id(String path) {
        return new Identifier(PolymerUtils.ID, path);
    }

    public static ItemStack getNoTextureItem() {
        if (NO_TEXTURE == null) {
            NO_TEXTURE = Items.PLAYER_HEAD.getDefaultStack();
            NO_TEXTURE.getOrCreateNbt().put("SkullOwner", PolymerUtils.createSkullOwner(PolymerUtils.NO_TEXTURE_HEAD_VALUE));
            NO_TEXTURE.setCustomName(LiteralText.EMPTY);
        }
        return NO_TEXTURE;
    }

    static {
        final String chr = "█";
        var icon = new ArrayList<MutableText>();
        try {
            var source = ImageIO.read(PolymerImpl.getJarPath("assets/icon_ingame.png").toUri().toURL());

            for (int y = 0; y < source.getHeight(); y++) {
                var base = new LiteralText("");
                int line = 0;
                int color = source.getRGB(0, y) & 0xFFFFFF;
                for (int x = 0; x < source.getWidth(); x++) {
                    int colorPixel = source.getRGB(x, y) & 0xFFFFFF;

                    if (color == colorPixel) {
                        line++;
                    } else {
                        base.append(new LiteralText(chr.repeat(line)).setStyle(Style.EMPTY.withColor(color)));
                        color = colorPixel;
                        line = 1;
                    }
                }

                base.append(new LiteralText(chr.repeat(line)).setStyle(Style.EMPTY.withColor(color)));
                icon.add(base);
            }
        } catch (Exception e) {
            e.printStackTrace();
            icon.add(new LiteralText("/!\\ [ Invalid icon file ] /!\\").setStyle(Style.EMPTY.withColor(0xFF0000).withItalic(true)));
        }

        ICON = icon.toArray(new Text[0]);
    }
}