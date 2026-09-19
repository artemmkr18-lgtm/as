package ez.minar.system.features.misc;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.FunctionManager;
import ez.minar.system.api.NewFunction;
import ez.minar.system.settings.impl.ModeSetting;
import ez.minar.system.settings.impl.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Locale;

@NewFunction(name = "Sounds", desc = "Проигрывает звуки при переключении модулей", category = Category.MISC)
public class Sounds extends Function {
    private final ModeSetting sound = new ModeSetting("Звук", "Дефолт", "Плавный", "Целка", "Блоп", "Module 5", "Module 6", "Module 7");
    private final NumberSetting volume = new NumberSetting("Громкость", 100.0, 0.0, 100.0, 1.0);

    public Sounds() {
        addSettings(sound, volume);
    }

    public static void playToggleSound(boolean enabled) {
        Sounds sounds = FunctionManager.getFunction(Sounds.class);
        SoundPack pack = sounds == null ? SoundPack.DEFAULT : SoundPack.byName(sounds.sound.getActiveMode());
        float volume = sounds == null ? 1.0f : (float) (sounds.volume.getValue() / 100.0);

        if (volume <= 0.0f) {
            return;
        }

        if (pack == SoundPack.DEFAULT) {
            playWav(enabled ? "assets/minar/sounds/on.wav" : "assets/minar/sounds/off.wav", volume);
        } else if (pack.usesWav()) {
            playWav(pack.wavPath(enabled), volume);
        } else {
            play(pack.event(enabled), volume);
        }
    }

    private static void play(Identifier id, float volume) {
        MinecraftClient client = MinecraftClient.getInstance();
        SoundManager soundManager = client.getSoundManager();
        if (soundManager == null) {
            return;
        }
        soundManager.play(PositionedSoundInstance.ui(SoundEvent.of(id), 1.0f, volume));
    }

    private static void playWav(String path, float volume) {
        Thread thread = new Thread(() -> {
            try (InputStream stream = Sounds.class.getClassLoader().getResourceAsStream(path)) {
                if (stream == null) {
                    return;
                }

                try (AudioInputStream audio = AudioSystem.getAudioInputStream(new BufferedInputStream(stream))) {
                    Clip clip = AudioSystem.getClip();
                    clip.addLineListener(event -> {
                        if (event.getType() == LineEvent.Type.STOP) {
                            clip.close();
                        }
                    });
                    clip.open(audio);
                    setClipVolume(clip, volume);
                    clip.start();
                }
            } catch (Exception ignored) {
            }
        }, "Minar-Sound");

        thread.setDaemon(true);
        thread.start();
    }

    private static void setClipVolume(Clip clip, float volume) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }

        FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float decibels = (float) (20.0 * Math.log10(Math.clamp(volume, 0.0001f, 1.0f)));
        gain.setValue(Math.clamp(decibels, gain.getMinimum(), gain.getMaximum()));
    }

    private enum SoundPack {
        DEFAULT("Дефолт", "default"),
        SMOOTH("Плавный", "smooth"),
        CELESTIAL("Целка", "celestial"),
        BLOP("Блоп", "blop"),
        MODULE_5("Module 5", "module5", 5),
        MODULE_6("Module 6", "module6", 6),
        MODULE_7("Module 7", "module7", 7);

        private final String name;
        private final String id;
        private final int wavIndex;

        SoundPack(String name, String id) {
            this(name, id, -1);
        }

        SoundPack(String name, String id, int wavIndex) {
            this.name = name;
            this.id = id;
            this.wavIndex = wavIndex;
        }

        private Identifier event(boolean enabled) {
            return Identifier.of("minar", id + (enabled ? "_on" : "_off"));
        }

        private boolean usesWav() {
            return wavIndex > 0;
        }

        private String wavPath(boolean enabled) {
            return "assets/minar/sounds/module_" + (enabled ? "enable" : "disable") + "_" + wavIndex + ".wav";
        }

        private static SoundPack byName(String name) {
            for (SoundPack pack : values()) {
                if (pack.name.equalsIgnoreCase(name) || pack.id.equals(name.toLowerCase(Locale.ROOT))) {
                    return pack;
                }
            }

            return DEFAULT;
        }
    }
}
