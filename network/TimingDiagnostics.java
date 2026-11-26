package network;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 將偶發超時寫在背景執行緒，避免診斷檔 I/O 阻塞遊戲迴圈。 */
public final class TimingDiagnostics {
    private static final long MILLION = 1_000_000L;
    private static volatile DiagnosticLog log;

    private TimingDiagnostics() {}

    public static synchronized void start(String role) {
        if (log != null && log.isAvailable()) return;
        try {
            Path directory = Path.of("diagnostics").toAbsolutePath();
            Files.createDirectories(directory);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path file = directory.resolve("timing-" + role + "-" + timestamp + ".log");
            BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
            log = new DiagnosticLog(writer, System.err::println);
            log.record("HaiKyuu timing diagnostics, schema=2, role=" + role
                    + ", app=" + model.AppVersion.LABEL + ", java=" + Runtime.version()
                    + ", processors=" + Runtime.getRuntime().availableProcessors()
                    + ", maxHeapMiB=" + Runtime.getRuntime().maxMemory() / (1024 * 1024));
            System.out.println("卡頓紀錄檔：" + file);
        } catch (IOException exception) {
            System.err.println("無法建立卡頓紀錄檔：" + exception.getMessage());
        }
    }

    public static void recordIfSlow(String operation, long startedAtNanos, long limitMillis) {
        DiagnosticLog current = log;
        if (current == null || !current.isAvailable()) return;
        long elapsed = System.nanoTime() - startedAtNanos;
        if (elapsed >= limitMillis * MILLION) {
            record(operation + " " + String.format("%.2f", elapsed / (double) MILLION) + " ms");
        }
    }

    public static void record(String detail) {
        DiagnosticLog current = log;
        if (current != null && current.isAvailable()) current.record(LocalDateTime.now() + " " + detail);
    }
}
