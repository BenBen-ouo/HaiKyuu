package network;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** 將偶發超時寫在背景執行緒，避免診斷檔 I/O 阻塞遊戲迴圈。 */
public final class TimingDiagnostics {
    private static final long MILLION = 1_000_000L;
    private static final LinkedBlockingQueue<String> lines = new LinkedBlockingQueue<>(4096);
    private static volatile boolean started;

    private TimingDiagnostics() {}

    public static synchronized void start(String role) {
        if (started) return;
        try {
            Path directory = Path.of("diagnostics").toAbsolutePath();
            Files.createDirectories(directory);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path file = directory.resolve("timing-" + role + "-" + timestamp + ".log");
            BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
            writer.write("HaiKyuu timing diagnostics, role=" + role + System.lineSeparator());
            writer.flush();
            started = true;
            Thread worker = new Thread(() -> writeLoop(writer), "haikyuu-timing-log");
            worker.setDaemon(true);
            worker.start();
            System.out.println("卡頓紀錄檔：" + file);
        } catch (IOException exception) {
            System.err.println("無法建立卡頓紀錄檔：" + exception.getMessage());
        }
    }

    public static void recordIfSlow(String operation, long startedAtNanos, long limitMillis) {
        long elapsed = System.nanoTime() - startedAtNanos;
        if (elapsed >= limitMillis * MILLION) {
            record(operation + " " + String.format("%.2f", elapsed / (double) MILLION) + " ms");
        }
    }

    public static void record(String detail) {
        if (started && !lines.offer(LocalDateTime.now() + " " + detail)) {
            // 診斷佇列滿時不阻塞遊戲；輸出明確訊息供使用者辨識紀錄不完整。
            System.err.println("卡頓紀錄佇列已滿，部分紀錄未寫入");
        }
    }

    private static void writeLoop(BufferedWriter writer) {
        try (writer) {
            while (!Thread.currentThread().isInterrupted()) {
                String line = lines.poll(1, TimeUnit.SECONDS);
                if (line != null) {
                    writer.write(line);
                    writer.newLine();
                    writer.flush();
                }
            }
        } catch (IOException | InterruptedException exception) {
            System.err.println("卡頓紀錄中止：" + exception.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}
