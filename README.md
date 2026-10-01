# HaiKyuu

Java 2D 火柴人排球遊戲。
可用單機雙人模式，或透過同一區域網路的 UDP Server 進行兩人連線。
選單提供「本地雙人」、「練習模式」、「創立房間」與「加入房間」。

## Windows 發行版

- 點兩下 `release/HaiKyuu/HaiKyuu.exe` 即可遊玩，不需另裝 JDK。
- 完整的遊戲檔案位於 `release/HaiKyuu/` 資料夾；可複製整個資料夾到其他路徑，不可只複製 `HaiKyuu.exe`。
- 兩位 Client（包含Server）必須在同一區域網路中。任一方離線會結束該局；下一局需重新啟動。

## 開發者啟動

### Windows PowerShell 手動重新編譯

```powershell
Remove-Item .\build -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path .\build -Force | Out-Null
javac -encoding UTF-8 -d .\build Main.java
```

### 手動執行

```powershell
# 圖形化啟動選單
java -cp .\build Main

# 直接啟動本地雙人：藍隊需使用方向鍵、數字鍵操控
java -cp .\build Main local

# 本地練習模式：紅隊練習接發與攻擊，藍隊固定發球，不計分
java -cp .\build Main practice

# 主機：啟動無畫面的 UDP Server（使用 UDP 5001）
java -cp .\build Main server

# 建立房間，同時讓房主以 UDP Client 加入
java -cp .\build Main host

# 兩位玩家各自在自己的電腦執行；<Server-IP> 替換為主機畫面顯示的 IPv4 位址
java -cp .\build Main join <Server-IP>
```

- 更新程式後再執行同一個 `build-release.bat` 即可重新打包產生發行版。若要從命令列執行而不暫停，可用 `build-release.bat --no-pause`。
- 打包過程的 JAR 只放在暫存的 `.release-build/`，成功後會清除；不另輸出 `dist/`，正式發行版只保留 `release/HaiKyuu/`。
- `Start Haikyuu.vbs`：專案根目錄的啟動捷徑開啟上述 EXE；若尚未打包，會提示先執行 `build-release.bat`。
- 「創立房間」會啟動 UDP Server，房主的 Client 使用畫面顯示的區網 IP 連入，不直接操作 Server 模型；找不到可用區網 IPv4 時會顯示錯誤且不啟動。
- 「加入房間」為加入房主顯示的區域網路 IPv4。雙方都透過相同的 Server 權威判定流程遊玩，使用 UDP 5001。
- 房主與加入者的遊戲視窗標題都顯示房主 IP；畫面右上角不重複顯示連線位址。
- 連線卡頓診斷會自動啟用，不需按任何鍵。紀錄檔位於啟動時工作目錄的 `diagnostics` 資料夾：透過專案根目錄的 VBS 啟動時在專案根目錄；直接雙擊發行版 EXE 時通常在 `release/HaiKyuu/` 內。GUI 房主的 Server 與 Client 同在一個程式中，寫入同一份 `timing-host-*.log`；訪客寫入 `timing-client-*.log`。若另外用命令列啟動 Server，則會有獨立的 `timing-server-*.log`。再次出現卡頓時，請記下時間並提供對應檔案；紀錄包含更新、繪圖、接球事件超時及快照中斷，不包含玩家按鍵或 IP。
