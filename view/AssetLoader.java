/*
圖片資源載入器，負責從 assets/images 或 assets 讀取圖片。
已載入過的圖片會快取，避免每幀重複讀取檔案。
*/
package view;

import java.awt.Image;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

public class AssetLoader {
    private final Map<String, Image> cache = new HashMap<>();

    public Image get(String fileName) {
        if (cache.containsKey(fileName)) {
            return cache.get(fileName);
        }

        BufferedImage image = null;

        File imageFolderFile = new File("assets/images", fileName);
        File assetRootFile = new File("assets", fileName);

        try {
            if (imageFolderFile.exists()) {
                image = ImageIO.read(imageFolderFile);
            } else if (assetRootFile.exists()) {
                image = ImageIO.read(assetRootFile);
            }

            // 強制轉成帶 alpha 的 ARGB 格式，確保透明通道被保留和一致
            if (image != null && image.getType() != BufferedImage.TYPE_INT_ARGB) {
                BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = converted.createGraphics();
                g2.drawImage(image, 0, 0, null);
                g2.dispose();
                image = converted;
            }
        } catch (IOException e) {
            image = null;
        }

        cache.put(fileName, image);
        return image;
    }
}