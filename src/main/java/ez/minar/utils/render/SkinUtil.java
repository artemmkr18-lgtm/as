package ez.minar.utils.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SkinUtil {
    private static final Map<String, Identifier> skinCache = new ConcurrentHashMap<>();
    private static final java.util.Set<String> loadingSet = ConcurrentHashMap.newKeySet();
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);

    public static Identifier getAvatar(String name) {
        if (skinCache.containsKey(name)) {
            return skinCache.get(name);
        }

        if (loadingSet.add(name)) {
            executor.submit(() -> {
                try {
                    URL url = new URL("https://mc-heads.net/avatar/" + name + "/64");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);

                    if (connection.getResponseCode() == 200) {
                        try (InputStream is = connection.getInputStream()) {
                            NativeImage image = NativeImage.read(is);
                            MinecraftClient.getInstance().execute(() -> {
                                NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "avatar_" + name.toLowerCase(), image);
                                texture.upload();
                                Identifier id = Identifier.of("minar", "avatar_" + name.toLowerCase());
                                MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
                                skinCache.put(name, id);
                            });
                        }
                    } else {
                        // Mark as failed so we don't retry and just keep using default skin
                        skinCache.put(name, null);
                    }
                } catch (Exception e) {
                    skinCache.put(name, null);
                }
            });
        }

        return null;
    }
}
