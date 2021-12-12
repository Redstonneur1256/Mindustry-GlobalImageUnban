package fr.redstonneur1256.giu;

import arc.Core;
import arc.Events;
import arc.KeyBinds;
import arc.input.KeyCode;
import arc.scene.event.InputEvent;
import arc.scene.ui.Dialog;
import arc.scene.ui.layout.Table;
import arc.struct.IntMap;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Reflect;
import mindustry.Vars;
import mindustry.entities.units.BuildPlan;
import mindustry.game.EventType;
import mindustry.game.Schematic;
import mindustry.gen.Call;
import mindustry.input.Binding;
import mindustry.logic.LExecutor;
import mindustry.mod.Mod;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.SettingsMenuDialog;
import mindustry.world.blocks.logic.LogicBlock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class GlobalImageUnban extends Mod {

    private static final Pattern DRAW_COLOR_PATTERN = Pattern.compile("draw color ([0-9]{1,3}) ([0-9]{1,3}) ([0-9]{1,3})");

    @Override
    public void init() {
        if(Vars.headless) {
            return;
        }

        Vars.ui.schematics = new SchematicsDialogOverride();
    }

    public static void useSchematic(Schematic schematic) {
        int count = 0, errors = 0;
        for(Schematic.Stile tile : schematic.tiles) {
            try {
                if(tile.block instanceof LogicBlock && tile.config instanceof byte[]) {
                    tile.config = updateLogic((byte[]) tile.config);
                    count++;
                }
            }catch(Exception exception) {
                Log.err("[GIU] Failed to unban processor", exception);
                errors++;
            }
        }
        if(errors == 0) {
            Vars.ui.announce("Updated [accent]" + count + " processors[]", 1.5F);
        }else {
            Vars.ui.announce("Updated [accent]" + count + " processors[] ([red]" + errors + " errors[])", 1.5F);
        }
        Vars.control.input.useSchematic(schematic);
    }

    private static byte[] updateLogic(byte[] source) throws Exception {
        try(DataInputStream input = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(source)))) {
            int version = input.read();
            if(version == 0) {
                return source;
            }

            byte[] code = new byte[input.readInt()];
            input.readFully(code);
            String codeString = new String(code, Vars.charset);
            String fixedCode = updateCode(codeString);

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            try(DataOutputStream output = new DataOutputStream(new DeflaterOutputStream(stream))) {
                output.write(version);

                byte[] fixedCodeBytes = fixedCode.getBytes(Vars.charset);
                output.writeInt(fixedCodeBytes.length);
                output.write(fixedCodeBytes);

                byte[] buffer = new byte[512];
                int length;
                while((length = input.read(buffer)) != -1) {
                    output.write(buffer, 0, length);
                }
            }
            return stream.toByteArray();
        }
    }

    private static String updateCode(String code) {
        String[] lines = code.split("\n");
        IntMap<ColorGroup> colors = new IntMap<>();
        Seq<String> currentColor = null;
        for(String line : lines) {
            if(line.startsWith("draw color ")) {
                Matcher matcher = DRAW_COLOR_PATTERN.matcher(line);
                if(!matcher.find()) {
                    throw new IllegalStateException("Only supports constant color variables");
                }
                int r = Integer.parseInt(matcher.group(1));
                int g = Integer.parseInt(matcher.group(2));
                int b = Integer.parseInt(matcher.group(3));
                int rgb = (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
                currentColor = colors.get(rgb, () -> new ColorGroup(line)).lines;
                continue;
            }
            if(currentColor != null && line.startsWith("draw rect")) {
                currentColor.add(line);
            }
        }


        Seq<ColorGroup> groups = colors.values().toArray();
        groups.shuffle();

        StringBuilder output = new StringBuilder();
        int instructions = 0;
        boolean flushed = true;
        for(ColorGroup group : groups) {
            output.append(group.color).append('\n');
            instructions++;

            group.lines.shuffle();
            for(String line : group.lines) {
                output.append(line).append('\n');
                flushed = false;

                if(++instructions >= LExecutor.maxGraphicsBuffer) {
                    output.append("drawflush display1\n");
                    flushed = true;
                    instructions = 0;
                }
            }
        }
        if(!flushed) {
            output.append("drawflush display1\n");
        }

        return output.toString();
    }

}
