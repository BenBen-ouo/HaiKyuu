package view;

import java.awt.*;
import model.*;

public class GameRenderer {
    private final AssetLoader assets = new AssetLoader();
    private final CourtRenderer courtRenderer = new CourtRenderer();
    private final PlayerRenderer playerRenderer = new PlayerRenderer(assets);
    private final BallRenderer ballRenderer = new BallRenderer(assets);

    public void render(Graphics2D g, GameModel model) {
        courtRenderer.draw(g);
        playerRenderer.drawTeam(g, model.redTeam, true);
        playerRenderer.drawTeam(g, model.blueTeam, false);
        ballRenderer.draw(g, model.ball);
        drawScore(g, model);
    }

    private void drawScore(Graphics2D g, GameModel model) {
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.drawString(model.redScore + " : " + model.blueScore, GameConfig.SCREEN_WIDTH / 2 - 28, 44);
    }
}