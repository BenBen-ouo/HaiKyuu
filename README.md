# HaiKyuu
2D online volleyball game

powershell重新編譯檔案：
Remove-Item .\build -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path .\build -Force | Out-Null
javac -encoding UTF-8 -d .\build Main.java

Server 啟動指令：java -cp .\build Main server
雙方玩家啟動指令：java -cp .\build Main join <以上Server的IP>
