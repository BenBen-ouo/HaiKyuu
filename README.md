# HaiKyuu

Java 2D 排球遊戲。可用單機雙人模式，或透過同一區域網路的 UDP Server 進行兩人連線。

## Windows PowerShell：重新編譯

```powershell
Remove-Item .\build -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path .\build -Force | Out-Null
javac -encoding UTF-8 -d .\build Main.java
```

## Windows 命令提示字元（cmd）：重新編譯

```bat
if exist build rmdir /s /q build
mkdir build
javac -encoding UTF-8 -d build Main.java
```

## 執行方式

```powershell
# 單機雙人測試
java -cp .\build Main

# 主機：啟動無畫面的 UDP Server（使用 UDP 5001）
java -cp .\build Main server

# 兩位玩家各自在自己的電腦執行；<Server-IP> 替換為主機畫面顯示的 IPv4 位址
java -cp .\build Main join <Server-IP>
```

Server 與兩位 Client 必須在同一區域網路中。任一方離線後，Server 會結束該局；下一局需重新啟動 Server 與兩個 Client。
