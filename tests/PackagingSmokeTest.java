import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import model.GameConfig;
import model.GameModel;
import view.AssetLoader;
import view.GameRenderer;
import view.LauncherPanel;

/** 從沒有外部 assets 的工作目錄執行，確認 JAR 內全部圖片可讀且畫面可繪。 */
public final class PackagingSmokeTest {
    public static void main(String[] imageNames) {
        if (imageNames.length == 0) {
            throw new AssertionError("沒有提供需要驗證的圖片清單");
        }

        AssetLoader assets = new AssetLoader();
        for (String imageName : imageNames) {
            if (AssetLoader.class.getResource("/assets/images/" + imageName) == null) {
                throw new AssertionError("JAR 缺少圖片資源：" + imageName);
            }
            Image image = assets.get(imageName);
            if (image == null || image.getWidth(null) <= 0 || image.getHeight(null) <= 0) {
                throw new AssertionError("無法解碼圖片：" + imageName);
            }
        }

        BufferedImage frame = new BufferedImage(
                GameConfig.SCREEN_WIDTH, GameConfig.SCREEN_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = frame.createGraphics();
        try {
            new GameRenderer().render(graphics, new GameModel());
        } finally {
            graphics.dispose();
        }
        if (frame.getRGB(GameConfig.SCREEN_WIDTH / 2, 0) == 0) {
            throw new AssertionError("球場畫面沒有繪製");
        }

        LauncherPanel launcher = new LauncherPanel("127.0.0.1", new LauncherPanel.Actions() {
            @Override public void startLocal() {}
            @Override public void startPractice() {}
            @Override public String startHost() { return null; }
            @Override public String join(String ip) { return null; }
        });
        if (launcher.getComponentCount() == 0) {
            throw new AssertionError("Launcher 沒有建立畫面元件");
        }
        System.out.println("PackagingSmokeTest passed: " + imageNames.length + " images");
    }
}
