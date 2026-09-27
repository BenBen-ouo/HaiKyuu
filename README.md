# HaiKyuu

Java 2D 排球遊戲。可用單機雙人模式，或透過同一區域網路的 UDP Server 進行兩人連線。

## 1. 先安裝 Java JDK

## 2. Windows：雙擊啟動

雙擊 `Start Haikyuu.bat`，從選單選擇 `1. Local two-player`（本地雙人）、`2. Practice`（練習模式）、`3. Host server only`（建立主機）或 `4. Join server`（連上主機）。啟動檔會自動編譯，不需要 VS Code 或 Java Extension。它會從 `JAVA_HOME`、PATH 或常見的 JDK 安裝資料夾尋找 Java；若 JDK 安裝在其他位置，請設定 `JAVA_HOME`。

「建立連線主機」只啟動無畫面的 Server，並在命令列顯示 IPv4 位址。要在同一台電腦加入遊戲，請再開一次啟動檔，選「連上主機」，在跳出的視窗輸入 `127.0.0.1`；其他電腦則輸入主機顯示的 IPv4 位址。兩位玩家都需各自開啟 Client。

## 若為 VSCode 執行

## 1. 一樣先安裝 Java JDK

## 2. Windows PowerShell 手動重新編譯

```powershell
Remove-Item .\build -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path .\build -Force | Out-Null
javac -encoding UTF-8 -d .\build Main.java
```

## 3. 手動執行

```powershell
# 單機雙人對打模式：籃隊需使用方向鍵、數字鍵操控
java -cp .\build Main

# 本地練習模式：紅隊練習接發與攻擊，藍隊固定發球，不計分
java -cp .\build Main practice

# 主機：啟動無畫面的 UDP Server（使用 UDP 5001）
java -cp .\build Main server

# 兩位玩家各自在自己的電腦執行；<Server-IP> 替換為主機畫面顯示的 IPv4 位址
java -cp .\build Main join <Server-IP>
```

Server 與兩位 Client 必須在同一區域網路中。任一方離線後，Server 會結束該局；下一局需重新啟動 Server 與兩個 Client。
