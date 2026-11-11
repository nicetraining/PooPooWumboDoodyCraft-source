package com.fartypoopy.sexmod.custommodel;

import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.entity.CompanionKind;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Original br: the custom models of the current world. A dedicated server reads {@code sexmod_custom_models/<name>/};
 * a client reads {@code sexmod/custom_models/<server address>/} (or {@code .../singleplayer/}), which a whitelisted
 * server fills by sending its models. Each model is a folder with {@code <name>.geo.json}, {@code <name>.png} and
 * {@code <name>.cfg}. Singleplayer shares this map between the client and the integrated server, as in the original.
 */
public final class CustomModels {
    public static final String CLIENT_ROOT = "sexmod/custom_models";
    public static final String WHITELIST = "sexmod/custom_models/whitelisted_servers.txt";
    public static final String SERVER_ROOT = "sexmod_custom_models";

    public enum Level { DEBUG, INFO, ERROR }

    /** Client hooks (texture / geo upload, chat output, current folder); null on a dedicated server. */
    public interface ClientSide {
        String folder();

        /** Loads the png and geo of a model; returns an error text or null. */
        String upload(String name, File folder);

        void unloadAll();

        void report(Level level, String message);
    }

    public static volatile ClientSide client;
    private static final Map<String, CustomModelConfig> MODELS = new ConcurrentHashMap<>();
    /** Original br.d: the last load failed, the wand reloads before opening the wardrobe. */
    public static volatile boolean loadFailed;

    private CustomModels() {
    }

    public static String folder() {
        ClientSide side = client;
        return side != null ? side.folder() : SERVER_ROOT;
    }

    static void report(Level level, String message) {
        ClientSide side = client;
        if (side != null) {
            side.report(level, message);
        } else if (level == Level.ERROR) {
            FapCraft.LOGGER.error(message);
        } else {
            FapCraft.LOGGER.info(message);
        }
    }

    /** Original br.b(boolean): clear, then load again. 0 when every model loaded. */
    public static int reload(boolean withAssets) {
        clear(withAssets);
        return load(withAssets);
    }

    /** Original br.a(boolean). */
    public static void clear(boolean withAssets) {
        ClientSide side = client;
        if (withAssets && side != null) {
            side.unloadAll();
        }
        MODELS.clear();
    }

    /** Original br.c(boolean): register every model folder; stops at the first broken one. */
    public static int load(boolean withAssets) {
        FapCraft.LOGGER.info("loading up custom models..."); // the log only: not in chat every time the game starts
        String root = folder();
        File rootFolder = new File(root);
        rootFolder.mkdirs();
        String[] names = rootFolder.list((dir, name) -> new File(dir, name).isDirectory());
        if (names == null) {
            report(Level.ERROR, String.format("Something is wrong with the custom models folder at '%s'. Check if it exists, if not - make the directory yourself because Minecraft cannot do it itself for some reason", rootFolder.getAbsolutePath()));
            return -1;
        }
        FapCraft.LOGGER.info(String.format("found %s custom model(s)", names.length));
        int count = 0;
        for (String name : names) {
            String error = checkFiles(name, root);
            if (error.isEmpty()) {
                error = register(name, root, withAssets);
            }
            if (!error.isEmpty()) {
                report(Level.ERROR, error);
                return -1;
            }
            count++;
        }
        report(Level.DEBUG, String.format("successfully registered %s custom models", count));
        return 0;
    }

    static String checkFiles(String name, String root) {
        String folder = root + "/" + name;
        File geo = new File(folder + "/" + name + ".geo.json");
        File png = new File(folder + "/" + name + ".png");
        File cfg = new File(folder + "/" + name + ".cfg");
        if (!geo.exists()) {
            return String.format("couldn't find model File for '%s'. It should have been at '%s'. Are you sure it exists?", name, geo.getAbsolutePath());
        }
        if (!png.exists()) {
            return String.format("couldn't find texture File for '%s'. It should have been at '%s'. Are you sure it exists?", name, png.getAbsolutePath());
        }
        if (!cfg.exists()) {
            return String.format("couldn't find cfg File for '%s'. It should have been at '%s'. Are you sure it exists?", name, cfg.getAbsolutePath());
        }
        return "";
    }

    /** Original br.a(String, String, boolean). */
    static String register(String name, String root, boolean withAssets) {
        if (MODELS.get(name) != null) {
            return String.format("already registered '%s'... honestly, unsure how this could happen lol", name);
        }
        File folder = new File(root + "/" + name);
        File cfgFile = new File(folder, name + ".cfg");
        byte[] cfg;
        try {
            cfg = Files.readAllBytes(cfgFile.toPath());
        } catch (IOException exception) {
            return String.format("couldn't find cfg File for '%s'. It should have been at '%s'. Are you sure it exists?", name, cfgFile.getPath());
        }
        CustomModelConfig config = CustomModelConfig.parse(name, cfg, cfgFile.getAbsolutePath());
        if (config.error != null) {
            return config.error;
        }
        if (!new File(folder, name + ".png").exists()) {
            return String.format("The texture for the custom model '%s' couldn't be found at '%s' are you sure it exists?", name, new File(folder, name + ".png").getPath());
        }
        if (!new File(folder, name + ".geo.json").exists()) {
            return String.format("The geo model for the custom model '%s' couldn't be found at '%s' are you sure it exists?", name, new File(folder, name + ".geo.json").getPath());
        }
        ClientSide side = client;
        if (withAssets && side != null) {
            String error = side.upload(name, folder);
            if (error != null) {
                return error;
            }
        }
        MODELS.put(name, config);
        report(Level.DEBUG, String.format("successfully registered custom model '%s'", name));
        return "";
    }

    public static boolean has(String name) {
        return MODELS.containsKey(name);
    }

    public static CustomModelConfig get(String name) {
        return name == null ? null : MODELS.get(name);
    }

    public static Map<String, CustomModelConfig> all() {
        return MODELS;
    }

    /** Original br.e(): every model with its version, sent to clients that ask. */
    public static Map<String, Float> versions() {
        Map<String, Float> versions = new HashMap<>();
        MODELS.forEach((name, config) -> versions.put(name, config.version));
        return versions;
    }

    /** Original br.a(em): the models a girl of this kind may wear, per slot. */
    public static Map<WearType, List<String>> modelsFor(CompanionKind kind) {
        Map<WearType, List<String>> slots = new EnumMap<>(WearType.class);
        for (WearType type : WearType.values()) {
            slots.put(type, new ArrayList<>());
        }
        MODELS.forEach((name, config) -> {
            if (config.fits(kind)) {
                slots.get(config.wearType).add(name);
            }
        });
        return slots;
    }

    /** Original em.Y(): the worn model names of a model code. */
    public static Set<String> parseCode(String code) {
        Set<String> names = new LinkedHashSet<>();
        if (code == null) {
            return names;
        }
        for (String part : code.split(WearType.SEPARATOR)) {
            if (!part.isEmpty() && !part.equals("cross")) {
                names.add(part);
            }
        }
        return names;
    }

    /** Original em.a(HashSet): the model code of a set of names ("a#b#"). */
    public static String toCode(Set<String> names) {
        StringBuilder code = new StringBuilder();
        for (String name : names) {
            code.append(name).append(WearType.SEPARATOR);
        }
        return code.toString();
    }

    /** Original em.c(List): girl-specific values as "1-2-3-". */
    public static String valuesToString(List<Integer> values) {
        StringBuilder text = new StringBuilder();
        for (int value : values) {
            text.append(value).append('-');
        }
        return text.toString();
    }

    /** Original em.c(String). Unparsable parts are skipped. */
    public static List<Integer> parseValues(String text) {
        List<Integer> values = new ArrayList<>();
        for (String part : text.split("-")) {
            try {
                values.add(Integer.parseInt(part));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return values;
    }

    /** Original br.l / br.f(): the servers the player allows to send custom models. */
    public static boolean whitelisted(String server) {
        return readWhitelist().contains(server);
    }

    /** Original br.h(String). */
    public static void whitelist(String server) {
        Set<String> servers = readWhitelist();
        servers.add(server);
        File file = new File(WHITELIST);
        file.getParentFile().mkdirs();
        try {
            Files.write(file.toPath(), (String.join("\n", servers) + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            FapCraft.LOGGER.warn("Could not write {}", file, exception);
        }
    }

    private static Set<String> readWhitelist() {
        Set<String> servers = new LinkedHashSet<>();
        File file = new File(WHITELIST);
        if (!file.exists()) {
            return servers;
        }
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    servers.add(line.trim());
                }
            }
        } catch (IOException exception) {
            FapCraft.LOGGER.warn("Could not read {}", file, exception);
        }
        return servers;
    }
}
