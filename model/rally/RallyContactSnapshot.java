package model.rally;

/** 一次擷取的回合接觸資料；不包含封包編碼或本機角色參照。 */
public record RallyContactSnapshot(
        TeamContacts red, TeamContacts blue, Boolean lastHitTeam,
        boolean lastTouchWasBlock, boolean serveReceptionComplete) {

    public record TeamContacts(int hitCount, int lastHitterIndex, boolean setterTouched,
            boolean setterTouchedFirst, boolean blockUsed, boolean attackLocked) {}
}
