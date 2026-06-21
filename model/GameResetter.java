/*
負責將整場遊戲重置為初始狀態。
重設分數、球、隊伍、觸球計數與發球狀態。
*/
package model;

public class GameResetter {

    // 將遊戲模型重置為初始狀態。
    // model 要重置的 GameModel 實例
    
    public static void reset(GameModel model) {
        // 重置分數
        model.redScore = 0;
        model.blueScore = 0;

        // 重置比賽狀態
        model.matchOver = false;
        model.matchWinnerRed = null;

        // 重置擊球計數與最後觸球者
        model.resetCounters();

        // 重置球的位置與速度
        model.ball = new Ball(GameConfig.SCREEN_WIDTH / 2.0, 130);

        // 重置隊伍狀態（球員位置與狀態）
        model.redTeam = new Team(true);
        model.blueTeam = new Team(false);

        // 重置發球狀態
        model.getServeHandler().reset();
    }
}
