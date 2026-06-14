package view;

import java.awt.Image;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

public class AssetLoader {
    private final Map<String, Image> cache = new HashMap<>();

    public Image get(String fileName) {
        if (cache.containsKey(fileName)) {
            return cache.get(fileName);
        }

        Image image = null;

        File imageFolderFile = new File("assets/images", fileName);
        File assetRootFile = new File("assets", fileName);

        try {
            if (imageFolderFile.exists()) {
                image = ImageIO.read(imageFolderFile);
            } else if (assetRootFile.exists()) {
                image = ImageIO.read(assetRootFile);
            }
        } catch (Exception e) {
            image = null;
        }

        cache.put(fileName, image);
        return image;
    }
}