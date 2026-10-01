# HaiKyuu

Java 2D 排球遊戲。可用單機雙人模式，或透過同一區域網路的 UDP Server 進行兩人連線。

## Windows 發行版（玩家不需安裝 Java）

開發者安裝 JDK 21 或更新版本後，雙擊 `build-release.bat`，即可重新編譯、執行測試，並產生包含 Java 執行環境的發行版。建置失敗會顯示錯誤並保留視窗；若要從命令列執行而不暫停，可用 `build-release.bat --no-pause`。

- `release/HaiKyuu/`：唯一的 Windows 發行資料夾，不另產生 ZIP。要交給其他玩家時，複製**整個**資料夾，雙擊其中的 `HaiKyuu.exe`；不可只複製 EXE，旁邊的 `app`、`runtime` 也必須保留。
- `Start Haikyuu.vbs`：專案根目錄的啟動捷徑，直接開啟上述 EXE；若尚未打包，會提示先執行 `build-release.bat`。
- `dist/HaiKyuu.jar`：供已安裝 Java 的環境使用，可執行 `java -jar dist/HaiKyuu.jar`。

更新程式後再執行同一個 `build-release.bat` 即可重新打包。建置產物及暫存資料夾不納入 Git。仍須在未安裝 Java 的 Windows 電腦實測啟動，以及實際兩台電腦連線；連線使用 UDP 5001，可能需要允許防火牆存取。

## 原始碼啟動（需安裝 Java JDK）

選單提供「本地雙人」、「練習模式」、「創立房間」與「加入房間」。創立房間會啟動 UDP Server，再讓房主以 UDP Client 連回 `127.0.0.1`，不直接操作 Server 模型；對方在同一選單的 IP 欄位輸入房主顯示的區域網路 IPv4。雙方都透過相同的 Server 權威判定流程遊玩，使用 UDP 5001。

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

Server 與兩位 Client 必須在同一區域網路中。任一方離線後，Server 會結束該局；下一局需重新啟動 Server 與兩個 Client。

## 跳躍發球

發球方按紅隊 `D + Space`、藍隊 `← + NumPad0`（或上方數字列 `0`）拋球。放開發球鍵後第二次按下只讓後排起跳；再放開，空中第三次按下可提早按住等待球進攻擊框，碰到球才真正發球。拋球後沒擊中、球落地會判「發球犯規」。拋球與起跳期間不能左右移動。

第三次按鍵觸球時，紅隊 `S`／藍隊 `↓` 稍短，紅隊 `A`／藍隊 `→` 減少水平速度，紅隊 `D`／藍隊 `←` 稍長；不按方向鍵為預設球路。連線時兩邊都用紅隊的 WASD 鍵位，Client 會自動鏡像藍隊的世界方向。

連線卡頓診斷會自動啟用，不需按任何鍵。紀錄檔位於啟動時工作目錄的 `diagnostics` 資料夾：透過專案根目錄的 VBS 啟動時在專案根目錄；直接雙擊發行版 EXE 時通常在 `release/HaiKyuu/` 內。GUI 房主的 Server 與 Client 同在一個程式中，寫入同一份 `timing-host-*.log`；訪客寫入 `timing-client-*.log`。若另外用命令列啟動 Server，則會有獨立的 `timing-server-*.log`。再次出現卡頓時，請記下時間並提供對應檔案；紀錄包含更新、繪圖、接球事件超時及快照中斷，不包含玩家按鍵或 IP。
