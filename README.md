# HaiKyuu

Java 2D 火柴人排球遊戲，提供本地雙人、左右方玩家對戰機器人，以及區域網路雙人對戰。

## 下載版啟動

從 GitHub Releases 下載 ZIP，完整解壓縮後開啟 `HaiKyuu/HaiKyuu.exe`。發行版已附 Java 執行環境，不需另裝 JDK；請保留整個資料夾。

## 從原始碼啟動

目前以 **JDK 21** 建置。先安裝 JDK，讓 `java`、`javac` 指向同一套 JDK，再於專案根目錄開啟 PowerShell：

```powershell
# 確認 Java 與編譯器版本
java -version
javac -version

# 編譯；失敗時停止，不執行舊的編譯結果
javac -encoding UTF-8 -d .\build Main.java
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed' }

# 開啟圖形化主選單
java -cp .\build Main
```

若找不到指令，請確認 `JAVA_HOME` 指向 JDK 資料夾，且 `PATH` 包含其 `bin`。修改程式後，重新執行編譯與啟動指令即可。

### 直接啟動指定模式

以下指令都在專案根目錄、完成編譯後執行：

| 模式 | 指令 |
| --- | --- |
| 本地雙人 | `java -cp .\build Main local` |
| 玩家在左、機器人在右 | `java -cp .\build Main practice` |
| 玩家在右、機器人在左 | `java -cp .\build Main practice-blue` |
| 創立房間，房主同時加入 | `java -cp .\build Main host` |
| 加入房間 | `java -cp .\build Main join 192.168.1.100(範例IP)` |
| 僅啟動 Server | `java -cp .\build Main server` |

加入房間時，將範例 IP 換成房主顯示的區網 IPv4。

## 與機器人對戰

在主選單調整防守／進攻能力（各 1～100，預設 50），再選擇玩家所在的一側。命令列直接啟動時使用預設能力。

進場後難度固定；`R` 重開保留難度、清空機器人的本場習慣統計。兩個模式均採正式計分，不再提供舊練習模式。

本地雙人與機器人對戰可用左上角「Pause」，按「繼續遊戲」後恢復；暫停期間按住的操作鍵需放開重按。連線模式不提供暫停。

## 區網連線

1. 雙方連上同一區域網路，使用相同版本。
2. 房主選「創立房間」，將顯示的 IPv4 分享給另一位玩家。
3. 另一位玩家在主選單輸入該 IP，再選「加入房間」。

使用 **UDP 5001**；請允許遊戲通過防火牆。房主也會以 Client 連入同一個 Server。找不到可用區網 IPv4 時無法創立房間；任一方離線會結束該局。

連線時會自動在啟動工作目錄的 `diagnostics/` 產生紀錄。回報卡頓時請附發生時間及同一局的紀錄；新版分開記錄收包、快照套用與繪圖階段，欄位說明見 [架構文件的診斷章節](ARCHITECTURE.md#9-錯誤輸入與診斷)。

## 打包 Windows 發行版

安裝完整 JDK 後，在專案根目錄執行：

```powershell
.\build-release.bat --no-pause
```

- 成品為 `release/HaiKyuu/` 與包含版本號的 ZIP；版本取自 `model/AppVersion.java`。
- 分享整個資料夾或 ZIP，不能只複製 EXE。重新打包前先關閉正在執行的發行版。
- 每項測試只顯示 `[PASS]`／`[FAIL]`；完整紀錄在 `build/release-tests.log`，每次覆寫。測試失敗會停止打包。
- 若提示 `.haikyuu-recovery-needed`，請保留 `.release-build/`，依錯誤檢查未完成的發行回復。
- 專案根目錄的 `Start Haikyuu.vbs` 會開啟已打包的 EXE；修改原始碼後須重新打包才會更新它。

## 開發文件

- [AGENTS.md](AGENTS.md)：協作規則、測試指令與驗證原則。
- [ARCHITECTURE.md](ARCHITECTURE.md)：模組與資料流、AI 實作、網路同步及診斷紀錄位置。
- [AI 能力說明](docs/AI_DIFFICULTY.md)：進攻／防守逐項門檻、區段差異與連續數值範圍。
