package com.fartypoopy.sexmod.custommodel;

import com.fartypoopy.sexmod.entity.CompanionKind;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Original br$b: the {@code <name>.cfg} of a custom model (java properties). The error texts are the original's; like the
 * original, a missing name / wear type / lighting stops the parse while a bad value is reported and parsing goes on (the
 * last error wins).
 */
public final class CustomModelConfig {
    public final String name;
    public WearType wearType;
    public String customBone;
    public final Set<CompanionKind> girls = EnumSet.noneOf(CompanionKind.class);
    public Lighting lighting;
    public String author;
    public final Set<String> bonesToHide = new HashSet<>();
    public boolean enableWhenNude;
    public float guiSizeFactor = 1.0F;
    public float guiVerticalPositioning = 0.0F;
    public float version;
    /** Null when the cfg is fine. */
    public String error;

    private CustomModelConfig(String name) {
        this.name = name;
    }

    /** True for names the original accepts and that cannot leave their folder (no path parts). */
    public static boolean validName(String name) {
        return name != null && !name.isEmpty() && name.length() <= 64
                && !name.contains(" ") && !name.contains("#") && !name.contains("$")
                && !name.equalsIgnoreCase("cross") && !name.contains("/") && !name.contains("\\")
                && !name.contains("..") && !name.contains(":") && name.chars().allMatch(c -> c >= 0x21 && c < 0x7F);
    }

    public static CustomModelConfig parse(String name, byte[] cfg, String path) {
        CustomModelConfig config = new CustomModelConfig(name);
        config.read(cfg, path);
        return config;
    }

    private void read(byte[] cfg, String path) {
        if (this.name.contains(" ") || this.name.contains("#") || this.name.contains("$")) {
            this.error = String.format("You cannot call your custom model '%s'. '#', '$' and spaces are illegal characters", this.name);
            return;
        }
        if (this.name.equalsIgnoreCase("cross")) {
            this.error = "You cannot call your custom model 'cross'. Im sorry, but I need that specific name for internal stuff";
            return;
        }
        if (!validName(this.name)) {
            this.error = String.format("You cannot call your custom model '%s'. '/', '\\', ':' and '..' are not allowed", this.name);
            return;
        }
        Properties properties = new Properties();
        try {
            properties.load(new ByteArrayInputStream(cfg));
        } catch (IOException | IllegalArgumentException exception) {
            this.error = String.format("couldn't read the cfg File for '%s' at '%s'. It appears to be corrupted. Try making a new one", this.name, path);
        }

        String wear = properties.getProperty("wear_type");
        if (wear == null) {
            this.error = String.format("The cfg File for the model '%s' at '%s' is missing the 'wear_type'. Go to the bottom of the cfg File and write 'wear_type=HEAD'. Check the cfg files of my examples to see what values for 'wear_type' are possible", this.name, path);
            return;
        }
        try {
            this.wearType = WearType.valueOf(wear.replace(" ", ""));
        } catch (IllegalArgumentException exception) {
            this.error = String.format("you entered '%s' into the 'wear_type' field of the %s's cfg file at '%s'. This is not a valid value. Check my examples on what valid values are to enter into the field 'wear_type", wear, this.name, path);
        }
        if (this.wearType == WearType.CUSTOM_BONE) {
            this.customBone = properties.getProperty("custom_bone");
            if (this.customBone == null || this.customBone.isEmpty()) {
                this.error = String.format("You selected CUSTOM_BONE as the 'wear_type' in the cfg file for '%s' at '%s', yet you left the 'custom_bone' field right underneath it empty. If you want ur model to be parented to a specific bone, you have to enter the name of that bone at the field 'custom_bone'.", this.name, path);
                return;
            }
        }

        String which = properties.getProperty("which_girls", "");
        for (String girl : which.replace(" ", "").split(",")) {
            if (girl.isEmpty()) {
                continue;
            }
            try {
                this.girls.add(CompanionKind.valueOf(girl));
            } catch (IllegalArgumentException exception) {
                this.error = String.format("you entered '%s' as one of the girls, you put into the 'which_girls' field of the %s's cfg file at '%s'. This is not a valid value. Check my examples on what valid values are to enter into the field 'which_girls'.", girl, this.name, path);
            }
        }

        String light = properties.getProperty("which_lighting");
        if (light == null) {
            this.error = String.format("The %s's cfg file at '%s' doesn't contain the field 'which_lighting'. Go to the bottom of the cfg file and write either 'which_lighting=DEFAULT', 'which_lighting=SEXMOD', or 'which_lighting=NONE'.", this.name, path);
            return;
        }
        try {
            this.lighting = Lighting.valueOf(light.replace(" ", ""));
        } catch (IllegalArgumentException exception) {
            this.error = String.format("you entered '%s' into the 'which_lighting' field of the %s's cfg file at '%s'. This is not a valid value. Check my examples on what valid values are to enter into the field 'which_lighting'.", light, this.name, path);
        }

        String author = properties.getProperty("author");
        this.author = author == null || author.isEmpty() ? "anon" : author;

        String hide = properties.getProperty("bones_to_hide");
        if (hide != null && !hide.isEmpty()) {
            this.bonesToHide.addAll(Arrays.asList(hide.replace(" ", "").split(",")));
        }

        String nude = properties.getProperty("enable_when_nude");
        this.enableWhenNude = nude != null && nude.replace(" ", "").equalsIgnoreCase("yes");

        String size = properties.getProperty("gui_size_factor");
        if (size != null && !size.isEmpty()) {
            try {
                this.guiSizeFactor = Float.parseFloat(size.replace(" ", "").replace(",", "."));
            } catch (NumberFormatException exception) {
                this.error = String.format("you entered '%s' into the 'gui_size_factor' field of the %s's cfg file at '%s'. This is not a valid value. Check my examples on what valid values are to enter into the field 'gui_size_factor'.", size, this.name, path);
            }
        }
        String vertical = properties.getProperty("gui_vertical_positioning");
        if (vertical != null && !vertical.isEmpty()) {
            try {
                this.guiVerticalPositioning = Float.parseFloat(vertical.replace(" ", "").replace(",", "."));
            } catch (NumberFormatException exception) {
                this.error = String.format("you entered '%s' into the 'gui_vertical_positioning' field of the %s's cfg file at '%s'. This is not a valid value. Check my examples on what valid values are to enter into the field 'gui_vertical_positioning'.", vertical, this.name, path);
            }
        }
        String versionString = properties.getProperty("version");
        try {
            this.version = Float.parseFloat(String.valueOf(versionString).replace(" ", "").replace(",", "."));
        } catch (NumberFormatException exception) {
            this.error = String.format("you entered '%s' into the 'versionString' field of the %s's cfg file at '%s'. This is not a valid value. Check my examples on what valid values are to enter into the field 'versionString'.", versionString, this.name, path);
        }
    }

    /** The bone the model hangs from. */
    public String attachBone() {
        return this.wearType == WearType.CUSTOM_BONE ? this.customBone : this.wearType.boneName;
    }

    /** Original br.a(em) filter: a model with no girls listed fits every girl. */
    public boolean fits(CompanionKind kind) {
        return this.girls.isEmpty() || this.girls.contains(kind);
    }
}
