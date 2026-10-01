/*
得分判斷工具，依照球落在哪一側與最後觸球隊伍決定誰得分。
若尚未有最後觸球隊伍，則以目前發球方作為暫定判斷依據。
*/
package model.rally;

import model.GameConfig;
import model.ball.Ball;

public class ScoringLogic {
    /** 球體壓到 x=100／1100 的白線外緣也算界內，左右兩端使用同一規則。 */
    public static boolean isBallInCourt(double ballX, double ballRadius) {
        double halfLineWidth = GameConfig.COURT_LINE_WIDTH / 2.0;
        return ballX + ballRadius >= GameConfig.COURT_LEFT_X - halfLineWidth
                && ballX - ballRadius <= GameConfig.COURT_RIGHT_X + halfLineWidth;
    }

    /**
     * 判斷哪一隊得分
     * @param ball 落地的球，包含球心與半徑
     * @param lastHitTeam 最後觸球隊伍 (true: 紅隊, false: 藍隊, null: 無人觸球)
     * @param redServing 當前發球方 (處理發球直接落地的情況)
     * @return true 代表紅隊得分，false 代表藍隊得分
     */
    public static boolean determineWinner(Ball ball, Boolean lastHitTeam, boolean redServing) {
        boolean isIn = isBallInCourt(ball.x, ball.radius);

        if (isIn) {
            // 界內：落在紅隊半場 (網子左邊) 則藍隊得分，反之紅隊得分
            return ball.x > GameConfig.NET_X;
        } else {
            // 界外：最後一個碰球的隊伍輸了 (對方得分)
            if (lastHitTeam != null) {
                return !lastHitTeam;
            } else {
                // 如果沒有人碰球 (例如發球直接出界)，發球方輸
                return !redServing;
            }
        }
    }
}
