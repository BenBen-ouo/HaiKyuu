package model;

// 專門負責判斷得分邏輯的類別

public class ScoringLogic {
    /**
     * 判斷哪一隊得分
     * @param ballX 球落地的 X 座標
     * @param lastHitTeam 最後觸球隊伍 (true: 紅隊, false: 藍隊, null: 無人觸球)
     * @param redServing 當前發球方 (處理發球直接落地的情況)
     * @return true 代表紅隊得分，false 代表藍隊得分
     */
    public static boolean determineWinner(double ballX, Boolean lastHitTeam, boolean redServing) {
        // 判斷是否在球場左右邊界內 (界內線)
        boolean isIn = ballX >= GameConfig.COURT_LEFT_X && ballX <= GameConfig.COURT_RIGHT_X;

        if (isIn) {
            // 界內：落在紅隊半場 (網子左邊) 則藍隊得分，反之紅隊得分
            return ballX > GameConfig.NET_X;
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
