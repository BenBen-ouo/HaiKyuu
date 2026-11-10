# HaiKyuu 專案架構

本文件記錄實作現況。協作與品質規則以 [AGENTS.md](AGENTS.md) 為權威；操作與發布引用 [README.md](README.md)。

更新：2026-11-07，`AIbot`，基準 commit `ed3ada0` 加目前未提交的 AI 門檻、補位、本地暫停、近網第三球與選單調整。發行版本 v1.2.0；以下已實作與尚待確認分開記錄。A／B／C／D／E 分類沿用 AGENTS；未另標示者為程式現況 C，不等於人工實測通過。

## 1. 系統目的與範圍

- Java Swing 2D 排球遊戲，含本地雙人、左／右半 AI 對戰、區網建立／加入房間；入口為 `Main.main`、`LauncherPanel`。
- 每隊 Back、Setter、MB、WS，由 `model.player.Team` 建立。AI 對戰完全取代舊練習功能；GameModel 不再保存練習模式／固定發球資料，所有對局共用正式計分。
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
| 本地 AI | `model.ai.RuleBasedAi`、`AiObservation`、攻防／發球 Planner、`OpponentHabits` | 只讀公開場況，產生相同 ControlInput；不修改模型或呼叫成功動作入口。 |
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
- AppVersion 是遊戲／發行版本來源；UdpCodec 的協定版本是不同概念。現行遊戲版本為 v1.2.0，協定版本為 12。
- 建置／發行／診斷目錄由 .gitignore 排除；不提交 bundled runtime。
- 沒有建立 Service、Repository、shared/common 或相容層空殼。
- 實際角色與模型目前沒有對外繼承擴充入口；具體類別使用 final，Player 的建構責任限於角色 package。GamePanel 持有對局執行資源，明確拒絕序列化／反序列化，不以 transient 欄位假裝可還原對局。

## 6. 主要資料與呼叫流程

### 6.1 本地與 AI 對戰

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

本地雙人與 AI 對戰的暫停由 GameController 持有，與 update 共用鎖定，避免 EDT 按鈕切換時模型仍更新。暫停期間不呼叫模型或 AI；GamePanel 保留繪圖迴圈並顯示確認繼續面板，NetworkView 模式不建立此控制項。KeyboardController 的 suspended／blockedUntilRelease 阻擋暫停前及期間的按鍵重複事件，放開再按後恢復。遊戲畫面不另繪製玩家／機器人對戰標籤；左上角 Pause 僅供本地模式使用。

一般第三球（後排、WS、Setter）在 RallyContactHandler 計算原速度後，才交 NetClearanceTrajectory 檢查近網路徑；只有原軌跡確定撞網且球心位於距網三米範圍內，才以離散幀重新求通過原目標點的 vx／vy，並檢查實際網子形狀、球半徑、淨空與世界上緣。已可過網、遠網及前兩球不修正。使用者已確認：極端貼網且保留目標與現有世界邊界時無解，保留原球路與正常觸網判定，不移球或改落點。這是本次明確的無解政策，不變更 BallTrajectory 原有降級。

- Launcher／NetworkAddress 驗證區網位址，Main 顯示啟動錯誤。
- Server 用 session token、地址與埠識別對局端點，不是帳號認證或 TLS。
- 網路技術失敗中止對局，不當作得分；建立連線與等待期間失聯也會逾時，Server 持續回應時可繼續等對手。
- codec 無效資料回傳 null；不替換為其他有效事件。AssetLoader 的缺圖回退行為保留。
- TimingDiagnostics 提供入口，DiagnosticLog 擁有有界佇列、writer 及故障停用；磁碟寫入及滿載回報不在遊戲更新執行緒執行。
- 診斷 schema=2 的檔頭包含遊戲版本、JDK、可用處理器數及最大 heap，不包含 IP 或按鍵。UdpClientTransport 在 socket.receive 返回後、解碼前帶入單調時鐘時間；不可將它當成網卡硬體到包時間。ReceivedWorld 保留時間與同一份不可變快照一起交給更新執行緒；SnapshotTiming 只記錄，不介入快照接受、排序、頻率或模型狀態。

| schema=2 紀錄 | 意義與門檻 |
| --- | --- |
| `snapshot receive gap` | 有效 WorldSnapshot 封包的應用層接收間隔，至少 50 ms；附快照序號及 serverTick |
| `snapshot apply gap` | 不同新序號真正套用的間隔，至少 50 ms；重套舊快照不重設時間 |
| `snapshot receive-to-apply` | 該份快照從接收到開始套用的等待，至少 25 ms，包含解碼與排程等待 |
| `snapshot apply work` | 套用球、角色、接觸狀態及視覺校正的工作耗時，至少 10 ms |
| `snapshot counts` | 約每 5 秒套用新快照時記錄累計 received／applied；兩者差異也可能來自正常的 latest-only 合併，不代表丟包 |
| `udp decode` | 接收後解碼耗時，至少 5 ms |
| `client controller update`／`render snapshot capture` | 模型／輸入更新與建立不可變畫面快照，分別至少 10 ms |
| `paint background`／`paint court`／`paint players`／`paint ball`／`paint effects`／`paint HUD`／`paint overlay` | Swing 背景、球場、角色、球、特效、比分與提示、連線提示，各階段至少 5 ms |
| `asset load 檔名` | 圖片首次讀取／解碼／格式轉換，至少 5 ms；快取命中不另記 |

舊 `client paint`（至少 25 ms）、更新迴圈與 Server 超時仍保留。以上都是 wall-clock 耗時，不等於純 CPU 工作；GC／排程暫停也可能包含在其中，單方紀錄仍不足以斷定線路丟包或 Server 原因。NetworkSyncTest 驗證收包正常但套用延遲、真正收包間隔、接收後等待、重套舊快照及累計統計；基本測試不建立真實 socket。
- 連線診斷自動啟用，紀錄在啟動工作目錄的 `diagnostics/`：根目錄 VBS 啟動時在專案根目錄，直接開 EXE 時通常在 `release/HaiKyuu/`。GUI 房主的 Server／Client 共寫 `timing-host-*.log`，訪客使用 `timing-client-*.log`，獨立 Server 使用 `timing-server-*.log`。回報卡頓時提供發生時間及對應檔案；內容記錄更新、繪圖、接球事件超時與快照中斷，不記玩家按鍵或 IP。
- 發行替換由 ReleaseReplacement.ps1 管理資料夾與 ZIP 回復；保留未完成標記，阻止下次清掉尚待恢復的產物。

## 10. 架構驗證

| 項目 | 防護與入口 | 實際限制 |
| --- | --- | --- |
| 模型依賴與循環 | ArchitectureTest.ps1／jdeps，已接打包 | 檢查禁止依賴及四層間的類別循環；保留已審查的模型內擁有者／協作者回參照，不宣稱所有 package 都無循環。 |
| 遊戲與狀態 | GameplayFlowTest，已接打包 | 含左右、落地邊界、焦點、補跑、畫面資料及迴圈關閉；不是實體 GUI。 |
| 網路 | NetworkSyncTest，基本模式已接打包 | 基本使用記憶體傳輸；動態埠 loopback 另執行，不等於雙機區網。 |
| 本地 AI | AiMatchTest、AiTacticsTest、AiPolicyTest、AiLearningTest、LauncherTest，已接打包 | 實際模型攻防、進攻轉換率、站位、節奏、門檻、鏡像、習慣對進攻安排與回防的影響、難度分離、R 重開及模式輸入；不是人工手感或實戰勝率保證。 |
| 本地暫停 | LocalPauseTest，已接打包 | 凍結模型、AI、判決計時及活動特效；放開再按、確認繼續；連線不顯示按鈕。實體操作仍需人工確認。 |
| 近網一般第三球 | ThirdTouchTrajectoryTest，已接打包 | 左右後排／WS／Setter 實際觸球、越網、保留目標、已可過網／遠網不改，以及無解時維持正常觸網政策。 |
| 發行回復 | ReleaseReplacementTest.ps1，已接打包 | 正常替換、舊 ZIP 鎖住及新 ZIP 鎖住失敗；不是所有 OS 故障注入。 |
| 素材與發行 | PackagingSmokeTest、JAR／ZIP 清單，已接打包 | 隔離目錄驗證素材；無 Java Windows 人工驗收仍另列。 |
| 效能 | RefactorBenchmark，手動入口 | 同機小型比較，不冒充 GPU、實體視窗或長時間壓測。 |

沒有 CI，未以文件要求冒充已啟用的自動檢查。

本階段收尾驗證（2026-11-07，來源為本文件開頭基準加未提交差異）：JDK 21.0.10 執行 `build-release.bat --no-pause`，退出碼 0；乾淨編譯與所有已接入回歸、發行回復、架構及隔離 JAR 的 39 張素材檢查通過。打包後 JAR 另編譯並執行 LocalPauseTest、ThirdTouchTrajectoryTest 與 NetworkSyncTest loopback，均通過，網路測試使用動態埠。v1.2.0 EXE 資料夾與 ZIP 已更新，內外遊戲 JAR 的 SHA-256 一致；紀錄為忽略的 `build/release-tests.log`。選單留白與暫停畫面完成離屏繪圖檢視；真人操作、雙機區網、無 Java Windows 及長時間運行尚未人工驗收。未 commit、push 或對外發布。

先前重構收尾驗證（commit `3deea06e9205eef59131f5d7ad74ccdcf4071f19` 加當時未提交差異，不代表本次 AI 來源已驗證）：JDK 21.0.10 全新輸出編譯並啟用 `javac -Xlint:all`，退出碼 0、零警告；GameplayFlowTest、LauncherTest、NetworkSyncTest 基本與 loopback、架構及發行回復檢查、隔離 JAR 的 39 張素材檢查均通過。當時版本 v1.1.0 的 app-image／ZIP 已重建，兩者遊戲 JAR 雜湊一致。

當次 RefactorBenchmark 以相同 JDK、相同 `-Xbatch` 條件比較三輪中位數，最大平均退化約 4.6%、p95 約 2.0%，符合當次 10%／15% 門檻；煙霧場景繪圖仍約 21.7 ms，未達 60 Hz 單幀預算。證據為忽略的本機產物 `build/closeout-batch-before.csv`、`build/closeout-batch-after.csv`；並非完整比賽效能保證。

## 11. 已確認限制與待決事項

- A：保留 Swing、未被本次需求取代的模式／按鍵／數值、Server 權威及同幀順序；未提交或對外發布，也未引入新框架。發行版本以 AppVersion 為準。
- Client 套用得分鎖定階段時，GameModel 同時清除兩隊後排的移動加速累計，避免下一球沿用舊累計；由 BackPlayer.resetRunAcceleration() 統一執行。
- A：使用者已確認不可達目標的既有降級、力道與球路維持現狀，日後遇到實際案例再議；例外的完整規範見 AGENTS §5，不再列為本次阻塞。
- E：正式 JDK 支援範圍與未來 CI 尚未另訂；本輪只記實際 JDK 與已接入的建置入口。
- 依賴盤點：jdeps 的強連通群主要為巢狀型別及模型內部的球／網子、Player／動畫／碰撞框、BackPlayer／DiveController、GameModel／回合／發球協作者回參照；沒有跨四層的類別循環。未為消除合法回參照而重設整個架構。
- 尚未以人工確認：真實雙機區網、實體 GUI／操作手感、無 Java Windows 與長時間運行。自動資源測試及效能取樣不涵蓋全部 OS 故障組合。

## 12. 本地規則式 AI

進攻／防守逐項能力、解鎖門檻、區段差異與連續數值範圍，統一維護於 [AI 能力說明](docs/AI_DIFFICULTY.md)。本節只記實際組裝、觀察邊界與資料流；參數以 AiTuning／AiDifficulty 為準。

- A：依本次使用者確認，兩個練習模式完全取代為左右 AI 對戰；玩家半場與按鍵維持，第一球紅隊發球，所有得分及重開使用正式模型。Main 組裝一個 RuleBasedAi，UDP 不建立 AI。
- GameController 每 tick 擷取 AiObservation，將 AI 所在隊伍的鍵盤輸入替換為 RuleBasedAi 產生的 ControlInput，再呼叫 GameModel.update；AI 不持有可變模型。觀察只含公開位置、速度、動作、已裁定的觸球及回合階段，不含對手按鍵／決策。
- 座標先轉成 AI 在左的視角，輸出時只轉換一次世界左右；球路模式不二次鏡像。生成輸入保留方向鍵與球路、後排動作與發球共鍵的關係。
- AiDifficulty 固定每場進攻／防守數值；AiTuning 保存決策參數。1～100 連續換算誤差、使用率及反應曲線；進階操作另有使用者確認的解鎖門檻，解鎖後使用率逐點增加。LauncherPanel 共用兩條拉條，進場後固定；R 同時重開模型並清空習慣，保留能力。
- DefensePlanner 對接觸／碰網後的新球路加入防守反應延遲，定期估計來球、分配 WS 短球與後排長球，並依公開助跑／起跳及習慣預判攔網。等待發球先居中準備；所有難度在對手接球後且球仍在對場組織時，都會先退回三米至九米的中央區域。入門仍沿用較慢反應與較大估計誤差，不因會退防而提升角色能力。實際來球後追接不加三米至九米限制。WS 撲球期間依公開速度、位置及現行碰撞框估計剩餘接球窗口，無法接到就解除原分工，由後排追接。補攔必須等 MB 正常落地，次數及高度仍由角色規則限制。
- OffensePlanner 在第一球後承諾戰術，以實際站位及含 Setter 跳躍的接觸估計，更新尚未起跳的可達窗口；到位且合法才輸出起跳，已起跳者不取消或瞬移。站舉負節奏的動作開始在 Setter 觸球前 42～48 幀，第二節奏約在球頂點開始後排起跳／WS 助跑。MB 只安排可達的快攻第一節奏，開網而無法覆蓋則改 WS／後排。
- AttackOptions 在實際球與攻擊框重疊時評估既有球路，優先選能過網且落在對場的選項；變化只在可用選項間抽樣。前四幀若有較好的正常扣球接觸點，可稍等而非一進框就吊球。假扣真舉包含 Setter 接第一球後的空中舉球備援，進攻 61 起才允許；60 以下只選可達攻擊或一般接球。若 WS 舉第二球，後排會先留在合法第三球起跳位置。進攻 41 起解鎖跳發與雙人進攻，61 起解鎖三人進攻；OffensePlanner 計入已起跳者，避免跨幀重複分配超過人數。
- 正常／低網／短球／天花板球及跳發變化均由 ServePlanner 按現有操作執行；跳發依序拋球、放開、起跳、放開再按擊球，沒有直接設置出球速度。戰術涵蓋吊球、現有合法二次攻擊、空中假扣真舉、Setter 跳舉及多人假跑／起跳；不新增 Setter 二次扣球。
- OpponentHabits 累計已發生的攻擊者、節奏、方向、吊球、發球類型及實際落地位置，以先驗樣本逐步調整防守。攻擊落點依角色與節奏分類；RuleBasedAi 只為未中斷的出手記首次落地，死球反彈不重複計入。OffensePlanner 使用同一份習慣評估後排起跳後的回防風險，DefensePlanner 在己方扣球飛出後即開始退防，準備位置隨深球習慣及防守能力調整。只記本場，不寫檔；每球保留、整場重開清空。參數及行為說明見 docs/AI_DIFFICULTY.md。
- BallForecast 是依當下位置／速度的有限自由飛行估計，並非最終落點答案；未來碰人／碰網不在預測內，發生後重新反應。AI 只選現有球路及動作，成功與失敗由正常碰撞／裁決產生；不改物理、碰撞箱或不可達目標降級。
- 限制：MB／Setter 無水平移動、MB 無一般接球、WS 只有既有助跑／回位／朝網撲救；AI 不能補足角色做不到的防守位置。三種節奏及多人戰術都以當次球路可達為條件，不保證每球執行成功。AI 沒有背景執行緒、等待或外部服務；決策工作隨遊戲迴圈結束。

AI 初版驗證（2026-11-06，來源為本文件開頭 commit 加當時 AI 功能差異）：JDK 21.0.10，`build-release.bat --no-pause` 退出碼 0，乾淨編譯啟用 `-Xlint:all`、零警告；GameplayFlowTest、LauncherTest、NetworkSyncTest、AiMatchTest、ArchitectureTest、ReleaseReplacementTest 及隔離 JAR 的 39 張素材檢查全部通過。當時 `AiMatchTest` 各以能力 10／50／95 推進 24,000 幀正式模型；此測試不足以證明進攻轉換率，後續另加 AiTacticsTest。正式發球原有亂數仍保留。

AI 初版 JAR 曾以 `javac -Xlint:all -encoding UTF-8 -cp release/HaiKyuu/app/HaiKyuu.jar -d build/ai-network-final tests/NetworkSyncTest.java` 編譯測試，再執行 `java '-Djava.awt.headless=true' -cp 'release/HaiKyuu/app/HaiKyuu.jar;build/ai-network-final' network.NetworkSyncTest loopback`，退出碼 0，使用動態埠。當時打包 log 在忽略的 `build/ai-release-verification.log`，版本為 v1.1.0；後續 v1.2.0 攔網提示的來源及驗證另記於下節，歷史結果不取代最新驗證。

當次測試的兩隊 AI 決策平均約 0.001～0.003 ms、p95 約 0.001～0.004 ms，僅包含觀察／決策，不包含繪圖與完整物理，亦非所有機器的效能保證。啟動頁已做離屏繪製檢視；真人手感、真實雙機區網及無 Java Windows 仍未人工驗收。

## 13. 攔網觸球提示

- A：使用者要求恢復攔網後的扣球軌跡，改以 MB 上方的隊伍顏色文字辨識觸球，並確認 AI Bot 功能版本為 v1.2.0。
- `RallyContactHandler` 沿用原攔網反彈與計次，`GameModel.recordBlock` 同時觸發 `BlockTouchEffect`；不再因攔網清除軌跡。提示預設 30 幀，跟隨 MB 正上方置中顯示；整場重開或歸位鎖定清除。
- Server 每 tick 依已確認的紅／藍攔網觸球送出可靠 `RED_BLOCK_CONTACT`／`BLUE_BLOCK_CONTACT`。Client 按事件 ID 去重並顯示，不能由按鍵或碰撞預測觸發；新增事件使 UDP 協定升為 12。一般快照不重啟提示計時。
- RenderFrame 擷取提示可見性，GameRenderer 在世界鏡像完成後交給 PlayerRenderer 畫字，保持文字正常方向、隊伍顏色及角色平滑位置；不依賴除錯框顯示開關。
- MB 起跳模式維持依本隊第一球觸球狀態決定。過網清除觸球紀錄後、下一次一般接球前，雙方可能同時攔網待命；球心在網子中心不是直接切換條件，空中動作不中途切換。

本次 v1.2.0 驗證：2026-11-06，JDK 21.0.10，`build-release.bat --no-pause` 退出碼 0、編譯零警告；基本遊戲、AI 對戰、Launcher、NetworkSyncTest、架構、發行回復及 39 張素材檢查通過。新增案例驗證攔網保留軌跡與落地煙霧、兩隊提示的顏色／鏡像位置／存續時間／R 清理，以及可靠事件亂序、去重和 RESET 清理；無背景連線的基本測試仍使用記憶體傳輸。

打包後再以最新 JAR 編譯 NetworkSyncTest 至 `build/touch-network-final`，執行 `java '-Djava.awt.headless=true' -cp 'release/HaiKyuu/app/HaiKyuu.jar;build/touch-network-final' network.NetworkSyncTest loopback`，退出碼 0，測試使用動態埠。EXE 與 `release/HaiKyuu-v1.2.0.zip` 已重建；log 為忽略的 `build/block-touch-release-verification.log`。未執行真人 GUI／雙機區網／無 Java Windows 驗收，未 commit、push 或對外發布。

## 14. AI 進攻與防守校正

- A：依使用者實測回報修復低進攻轉換率、接球未完成就退位、開網 MB 空跳、跳舉時機、負／第二節奏及後排踩線；保留手動調整的跳發短球與 MB 二次攔網參數。主選單高度縮至 370，按鈕約 29 像素；TOUCH 改為 MB 正上方置中。
- C：修正前以兩側、各 24 個 AI 亂數種子，從第一球已接起的正式模型開始；能力 61 的後排／Setter 第一球案例各只有 4／48 次扣球過網，能力 100 為 6／48 與 4／48。原先「能完成比賽」不能證明進攻有效。
- OffensePlanner 不再把假設走到的位置當成已到位；後排必須實際抵達且起跳中心未踩線才按鍵。BackPlayer 提供同一份候選站位起跳速度計算；WingSpiker 的助跑距離估計扣除啟動動畫但未移動的首幀，實際玩家動作不改。
- Setter 接觸估計納入自身跳躍軌跡；高能力優先嘗試可達的跳舉。若 MB 的實際預估球路不進框就不排起跳，改由其他可達角色處理。Setter 第一球後可轉 WS／後排攻擊或跳舉；WS 跳舉時後排留作第三球支援，不提前作無效假跳。
- DefensePlanner 保留已準備好的站位／本次來球接球目標，避免發球後反應等待期被舊預設值拉向網前。後續依 WS 是否仍可接球調整分工，一般接球確認後才轉入進攻組織。三米至九米是等待發球與所有難度對手組織進攻時的預備區域，並非追球界限。後排以當下速度、原有分段加速與有限自由飛行估計站立接球窗口；跑不到時再比較兩方向撲救的減速與碰撞框窗口。DiveController.geometryFor 是實際撲球框配置的共用查詢，不另改角色能力或碰撞。
- 隨機範圍：站舉負節奏在觸球前 42～48 幀開始動作；第二節奏在球頂點附近開始動作，排程擾動限 ±2 幀。移除原本最多 22 幀的額外出手抽樣；進框後用當下位置選球路，至多等待 4 幀內更合適的正常扣球接觸點。能力 61 的接球位置誤差由約 ±32.6 像素降至 ±8.7，100 為 ±2；球路評分雜訊由 40 降至 8，變化只在可用球路中選擇，不刻意抽中必定掛網的角度。
- ServePlanner 跳發第三次按鍵須球已進攻擊框，並依當下接觸點檢查現有球路；使用者手動修改後若某變化無法過網，改選其他現有球路，不覆寫力道。
- AiTacticsTest 已接入日常及打包入口，驗證接發轉進攻、合法起跳、接球責任、等待發球居中站位、越出站位區追接、WS 撲早後後排補接、開網不排 MB、後排替代跳舉、實際助跑距離、負／第二節奏與跳發可達性。AiMatchTest 一般能力推進 48,000 幀；入門也會退防後來回較長，推進 96,000 幀並保留打完整場的檢查，涵蓋 40／41、60／61 解鎖邊界、普通雙人及高難度三人實際動作；吊球計數依攻擊當幀輸入，排除跳發，不用反彈後球速反推球種。

本次定點結果：正常發球情境能力 61／100 各 48／48 次接發後攻擊過網；已接起的後排第一球各 48／48，Setter 第一球各 44／48，三米線犯規為 0。WS 不可用的指定情境中，後排有 6／24 次實際跳舉第二球；跳發 18 次擊球全數過網。這些都是無對手主動攔網的基本能力案例，不等於對真人的得分率或勝率；人工實戰平衡仍需回饋。

本次驗證來源為 `AIbot` 基準 commit `86ce35d91541580aa17c669ef868a48762a53603` 加目前未提交差異，包含使用者手動設定 MB 二次起跳倍率 0.8 與跳發短球垂直速度 2.0。JDK 21.0.10 的 `build-release.bat --no-pause` 退出碼 0，乾淨編譯零警告，GameplayFlowTest、LauncherTest、NetworkSyncTest、AiMatchTest、AiTacticsTest、ArchitectureTest、ReleaseReplacementTest 與隔離 JAR 的 39 張素材檢查通過。較小選單與 TOUCH 置中已有離屏繪圖／幾何驗證。

更新後 JAR 另編譯 NetworkSyncTest 至 `build/ai-tactics-network`，執行 `java '-Djava.awt.headless=true' -cp 'release/HaiKyuu/app/HaiKyuu.jar;build/ai-tactics-network' network.NetworkSyncTest loopback`，退出碼 0，使用動態埠並完成資源清理。`release/HaiKyuu/` 與 `release/HaiKyuu-v1.2.0.zip` 已更新，兩者遊戲 JAR 雜湊一致；完整 log 在忽略的 `build/ai-tactics-release-verification.log`。兩隊 AI 決策本機取樣平均約 0.004～0.006 ms，p95 約 0.021～0.028 ms，不包含整體繪圖成本。未宣稱真人實戰、雙機區網或無 Java Windows 人工驗收通過；未 commit、push 或對外發布。
