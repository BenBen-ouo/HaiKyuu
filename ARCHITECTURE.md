# HaiKyuu 專案架構

本文件記錄實作現況。協作與品質規則以 [AGENTS.md](AGENTS.md) 為權威；操作與發布引用 [README.md](README.md)。

更新：2026-11-06，`refactor`，收尾基準 commit `3deea06e9205eef59131f5d7ad74ccdcf4071f19` 加本次未提交差異。使用者已核准整體重構；以下已實作與尚待確認分開記錄。A／B／C／D／E 分類沿用 AGENTS；未另標示者為程式現況 C，不等於人工實測通過。

## 1. 系統目的與範圍

- Java Swing 2D 排球遊戲，含本地雙人、左／右方練習、區網建立／加入房間；入口為 `Main.main`、`LauncherPanel`。
- 每隊 Back、Setter、MB、WS，由 `model.player.Team` 建立。練習模式共用規則，只由 GameModel 模式資料控制不計分與固定發球方。
- UDP Server 與兩個 Client；GUI 房主在同一程序執行 Server 與自己的 Client，仍連至 Server 的區網 IP。
- 依賴桌面、鍵盤、圖片素材與區網；發行版附 Java runtime。沒有 HTTP、資料庫、帳號、配對服務或遊戲存檔。

## 2. 技術與執行環境

| 項目 | 現況及來源 |
| --- | --- |
| Java／GUI | JDK 21.0.10 已驗證；`javax.swing`、`java.awt`。正式支援的 JDK 範圍未另訂。 |
| 建置 | `javac`、`jar`、`jpackage`；沒有 Maven／Gradle、module descriptor 或第三方 Java 套件。 |
| 測試 | `tests/` 的 Java main 測試、PowerShell 發行／架構檢查；沒有 JUnit。 |
| 網路 | `DatagramSocket`、`UdpCodec`、`NetworkStateCodec`；正式連接埠由 GameServer 定義，測試另用動態埠。 |
| 發行 | `build-release.bat` → `tools/build-release.ps1`，產生 app-image 與版本 ZIP；VBS 啟動已建置 EXE。 |
| JDK 尋找 | 打包依 JAVA_HOME、PATH、安裝目錄尋找完整工具組；沒有把個人安裝路徑寫入專案。 |
| CI／持久化 | 沒有 CI workflow、資料庫、ORM、migration 或外部 API。 |

使用與日常命令見 README、AGENTS §9。

## 3. 架構風格與依賴方向

沿用 MVC 分工與按功能拆分的模型，沒有改用 ECS／MVVM：

```text
Main → controller、model、network、view
controller → model、network、view.DebugSettings
network → model
view → model、network 的顯示／診斷入口
model 子模組 → model 與必要的 model 子模組
```

- 模型禁止依賴 View／Controller／network 的規範見 AGENTS §3。
- `tests/ArchitectureTest.ps1` 用 jdeps 檢查編譯後的類別依賴，已接入發行入口；不只搜尋 import。同時拒絕跨 model／view／controller／network 的類別循環；層內擁有者回參照不等於禁止的跨層循環。職責合理性仍須人工審查。
- `controller.GameController` 切換 `view.DebugSettings`、Launcher 使用 `network.NetworkAddress` 是保留的現況，未增加泛用共用層。
- 模型使用必要 AWT 幾何型別，並不操作 Swing 視窗。

## 4. 各層責任與入口

規範上的邊界見 AGENTS §3；本表只定位實際實作。

| 模組 | 類別／入口 | 現況 |
| --- | --- | --- |
| 啟動組裝 | `Main` | 啟動參數、模式、Server／Client 與視窗關閉監聽。 |
| 操作調度 | `KeyboardController`、`GameController` | 讀取 ControlInput；失焦清鍵；送本地模型或 Client。 |
| 世界總調度 | `GameModel.update/updateForNetworkPrediction/restart` | 權威更新與角色預測分開；統籌整場重設。 |
| 回合 | `RallyState`、`RallyContactSnapshot`、`RallyContactHandler`、`RallyScorer`、`ScoringLogic` | 唯一觸球計數、具名快照、碰撞規則、判決與得分階段；模型不接收十多個位置參數。 |
| 球／球路 | `Ball`、`BallTrajectory`、`NetHitBox` | 物理、幾何、離散舉球／拋球與指定力道球路計算。 |
| 角色 | `Team`、各 Player、`PlayerPhysics`、`DiveController`、動畫類別 | 動作請求、移動、碰撞框與動畫階段；成功觸球由回合流程確認。 |
| 發球 | `ServeHandler`、`ServeBallController`、`ServeInputLocker`、`ServeContactPolicy` | 發球階段、擺球、輸入過濾及出球。 |
| 特效 | `SpikeEffect` | 軌跡與煙霧的可變生命週期；HSB、透明度與線寬由 SpikeEffectRenderer 擁有。 |
| 同步 | `GameServer`、`GameClient`、`ServerPlayerSlot`、Packet／codec | 傳輸、輸入樣本、可靠事件與權威快照套用。 |
| 傳輸邊界 | `ClientTransport`、`UdpClientTransport` | 固定遠端資料報端點與資源關閉；測試可替換成記憶體傳輸。 |
| 圖片校正 | `RemotePlayerInterpolator`、`PlayerRenderCorrection` | 對手插值／有限外推、本機圖片平滑；不改碰撞位置。 |
| 畫面 | `RenderFrame`、`GamePanel`、各 Renderer | 更新邊界擷取不可變資料，EDT 只讀發布後的畫面。 |
| 排程／診斷 | `FixedTickSchedule`、`TimingDiagnostics`、`DiagnosticLog` | 有界補跑、背景寫入與故障停用；沒有放入玩法或繪圖規則。 |

## 5. 模組、目錄與設定

- Java 原始碼為 Main 與 `controller/`、`model/`、`network/`、`view/`；測試與工具各在 `tests/`、`tools/`。
- `NetworkSyncTest` 宣告 package network，以測試傳輸邊界；其他 Java 測試仍在未命名 package。
- `assets/images/` 由 AssetLoader 優先從 JAR 載入，再回退開發目錄；圖片不再作為攔網碰撞階段的判斷來源。
- GameConfig 是全域調參入口，Team 管理角色碰撞框，Renderer 管理外觀。起跳 Y 參數以 `*_JUMP_VELOCITY_Y` 命名，保留使用者原數值。
- 發球／扣球的正值水平速率以 `HORIZONTAL_SPEED` 命名，帶符號垂直分量以 `VELOCITY_Y` 命名；ServeType 也分開描述速率與分量，沒有改數值或球路。
- AppVersion 是遊戲／發行版本來源；UdpCodec 的協定版本是不同概念。本次協定升為 11，遊戲版本未升。
- 建置／發行／診斷目錄由 .gitignore 排除；不提交 bundled runtime。
- 沒有建立 Service、Repository、shared/common 或相容層空殼。
- 實際角色與模型目前沒有對外繼承擴充入口；具體類別使用 final，Player 的建構責任限於角色 package。GamePanel 持有對局執行資源，明確拒絕序列化／反序列化，不以 transient 欄位假裝可還原對局。

## 6. 主要資料與呼叫流程

### 6.1 本地與練習

`KeyboardController → ControlInput → GameModel.update → TeamInput 當幀上下文 → 角色 → 觸球／判決 → RenderFrame → EDT`。

權威 tick 保留原順序：

1. 輸入釋放保護、發球前置、後排操作解讀及角色上下文。
2. 更新兩隊角色，嘗試空中舉球，保存攻擊重疊及起跳資料。
3. 跳發出手、球更新、碰網與過網重設。
4. 未由空中舉球處理時，紅隊後藍隊；隊內先攻擊，再依原角色順序查攔網／一般框。
5. 落地判決；本幀已碰球可取消此次落地。
6. 發球幀旗標、特效與暫時訊息更新。

A：同幀優先順序保留，公平性調整不在本輪；不能由現有順序推定一定公平或一定不公平。

### 6.2 網路

`GameClient → UDP 輸入 → ServerPlayerSlot → GameServer.updateOneTick → 共用 GameModel → 可靠事件／WorldSnapshot → GameClient → RenderFrame`。

- 接收執行緒保留最新世界快照及其他訊息佇列，Client 更新時處理；可靠事件依序且去重，快照不等待無關事件。
- Server 從有界佇列依序消費完整輸入樣本；相同持續輸入合併，過期序號拒絕，不跨樣本合成組合鍵。
- HELLO 在 Server 更新邊界處理並擷取 WELCOME，接收執行緒不讀取整份模型。
- Client 只預測自身操作；成功扣球／空中舉球等待權威。球心、旋轉與碰撞採 Server；落地煙霧依確認事件。
- 對手圖片插值，本機圖片校正；MB 圖片及攔網階段採權威快照，除錯框仍使用物理位置。

### 6.3 得分、重設與退出

- RallyScorer 負責得分、下一球及賽末階段；MatchDisplay 讀取 RenderFrame.Match。
- 本地重開與 Server 雙方確認重開都呼叫完整 GameModel.restart；Client RESET 先清整場舊狀態，再套權威快照。
- 關窗由 Main 停止 GamePanel，關閉 Client／房主 Server。尚未新增返回主選單。
- Client close 在已中止、重複關閉或初始傳送失敗時仍清理傳輸與重送器；Server 未 run 即關閉也釋放 socket。
- 已開始對局的 Server 終止沿用 ACK 寬限；UI 不等待此期限。自動測試檢查執行緒退出與埠釋放。

## 7. 資料模型與邊界

| 型別 | 所有權與跨界方式 |
| --- | --- |
| ControlInput | 只有操作狀態，可編解碼及鏡像；沒有 Ball／Player 參照。 |
| TeamInput | 模型從操作副本加上當幀觸球／攻擊上下文；不作封包解碼型別。 |
| GameModel／RallyState／ServeHandler | 記憶體中的世界、觸球、發球狀態；沒有重複公開觸球計數。 |
| Player／Ball／碰撞框 | 更新執行緒持有可變資料；攻擊與攔網由 PlayerAction／BlockPhase 與動畫序列位置表示，移除重複 attacking／blocking 旗標。 |
| Packet.*State | 欄位建構時帶齊且 final；隊伍為不可變集合，不持有呼叫端可變陣列。 |
| RenderFrame | 深層不可變的角色、球、碰撞框、煙霧形狀、軌跡、判決及連線資料；不持有可變模型／NetworkView。 |
| SpikeEffect | 由更新執行緒維護；資料擷取後交 View，不由 EDT 遍歷原集合。 |

codec 拒絕無效事件／控制、非有限或超範圍物理資料、非法尺寸及不完整隊伍；起跳中心的 NaN 依「沒有起跳」語意允許。詳細欄位與順序只在 codec 維護。

## 8. 執行緒與生命週期

| 執行環境 | 實際責任 |
| --- | --- |
| Swing EDT | 視窗、鍵盤、只讀 RenderFrame 的繪圖。 |
| GamePanel 迴圈 | 本地更新或 Client 同步／預測；擷取後透過 volatile 參照發布畫面。 |
| Server 迴圈 | 權威 tick、HELLO／WELCOME、控制、事件與世界快照。 |
| 網路接收執行緒 | 接收及分類封包；不執行角色／球的更新。 |
| 重送排程器 | 可靠事件或控制重送與期限；關閉時停止。 |
| 診斷執行緒 | 程序共用背景 writer；不是每場對局的新服務，故障停用且清空。 |

FixedTickSchedule 限制補跑，不放大物理步長。更新失敗明確回報並釋放相關資源；長時間運行與 OS／真實網路極端故障仍需實測。

## 9. 錯誤、輸入與診斷

- Launcher／NetworkAddress 驗證區網位址，Main 顯示啟動錯誤。
- Server 用 session token、地址與埠識別對局端點，不是帳號認證或 TLS。
- 網路技術失敗中止對局，不當作得分；建立連線與等待期間失聯也會逾時，Server 持續回應時可繼續等對手。
- codec 無效資料回傳 null；不替換為其他有效事件。AssetLoader 的缺圖回退行為保留。
- TimingDiagnostics 提供入口，DiagnosticLog 擁有有界佇列、writer 及故障停用；磁碟寫入及滿載回報不在遊戲更新執行緒執行。
- 發行替換由 ReleaseReplacement.ps1 管理資料夾與 ZIP 回復；保留未完成標記，阻止下次清掉尚待恢復的產物。

## 10. 架構驗證

| 項目 | 防護與入口 | 實際限制 |
| --- | --- | --- |
| 模型依賴與循環 | ArchitectureTest.ps1／jdeps，已接打包 | 檢查禁止依賴及四層間的類別循環；保留已審查的模型內擁有者／協作者回參照，不宣稱所有 package 都無循環。 |
| 遊戲與狀態 | GameplayFlowTest，已接打包 | 含左右、落地邊界、焦點、補跑、畫面資料及迴圈關閉；不是實體 GUI。 |
| 網路 | NetworkSyncTest，基本模式已接打包 | 基本使用記憶體傳輸；動態埠 loopback 另執行，不等於雙機區網。 |
| 發行回復 | ReleaseReplacementTest.ps1，已接打包 | 正常替換、舊 ZIP 鎖住及新 ZIP 鎖住失敗；不是所有 OS 故障注入。 |
| 素材與發行 | PackagingSmokeTest、JAR／ZIP 清單，已接打包 | 隔離目錄驗證素材；無 Java Windows 人工驗收仍另列。 |
| 效能 | RefactorBenchmark，手動入口 | 同機小型比較，不冒充 GPU、實體視窗或長時間壓測。 |

沒有 CI，未以文件要求冒充已啟用的自動檢查。

2026-11-06 收尾驗證（基準為本文件上方 commit 加當次未提交差異）：JDK 21.0.10 全新輸出編譯並啟用 `javac -Xlint:all`，退出碼 0、零警告；GameplayFlowTest、LauncherTest、NetworkSyncTest 基本與 loopback、架構及發行回復檢查、隔離 JAR 的 39 張素材檢查均通過。版本 v1.1.0 的 app-image／ZIP 已重建，兩者遊戲 JAR 雜湊一致。這是當次來源的結果，後續修改須重新驗證。

當次 RefactorBenchmark 以相同 JDK、相同 `-Xbatch` 條件比較三輪中位數，最大平均退化約 4.6%、p95 約 2.0%，符合當次 10%／15% 門檻；煙霧場景繪圖仍約 21.7 ms，未達 60 Hz 單幀預算。證據為忽略的本機產物 `build/closeout-batch-before.csv`、`build/closeout-batch-after.csv`；並非完整比賽效能保證。

## 11. 已確認限制與待決事項

- A：保留 Swing、既有模式／按鍵／數值、Server 權威及同幀順序；沒有升版、提交、發布或引入新框架。
- Client 套用得分鎖定階段時，GameModel 同時清除兩隊後排的移動加速累計，避免下一球沿用舊累計；由 BackPlayer.resetRunAcceleration() 統一執行。
- A：使用者已確認不可達目標的既有降級、力道與球路維持現狀，日後遇到實際案例再議；例外的完整規範見 AGENTS §5，不再列為本次阻塞。
- E：正式 JDK 支援範圍與未來 CI 尚未另訂；本輪只記實際 JDK 與已接入的建置入口。
- 依賴盤點：jdeps 的強連通群主要為巢狀型別及模型內部的球／網子、Player／動畫／碰撞框、BackPlayer／DiveController、GameModel／回合／發球協作者回參照；沒有跨四層的類別循環。未為消除合法回參照而重設整個架構。
- 尚未以人工確認：真實雙機區網、實體 GUI／操作手感、無 Java Windows 與長時間運行。自動資源測試及效能取樣不涵蓋全部 OS 故障組合。
