package network;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** 背景寫入器擁有 writer；失敗後停用並清空，重新啟用須建立新實例。 */
final class DiagnosticLog implements AutoCloseable {
    private static final int MAX_PENDING_LINES = 4096;
    private final ArrayBlockingQueue<String> lines = new ArrayBlockingQueue<>(MAX_PENDING_LINES);
    private final AtomicBoolean available = new AtomicBoolean(true);
    private final AtomicBoolean overflowOccurred = new AtomicBoolean();
    private final Consumer<String> report;
    private final Thread worker;

    DiagnosticLog(BufferedWriter writer, Consumer<String> report) {
        this.report = report;
        worker = new Thread(() -> writeLoop(writer), "haikyuu-timing-log");
        worker.setDaemon(true);
        worker.start();
    }

    synchronized void record(String line) {
        if (!available.get()) return;
        if (!lines.offer(line)) overflowOccurred.set(true);
    }

    boolean isAvailable() { return available.get(); }

    private void writeLoop(BufferedWriter writer) {
        boolean overflowReported = false;
        try (writer) {
            while (available.get()) {
                String line = lines.take();
                writer.write(line);
                writer.newLine();
                writer.flush();
                if (overflowOccurred.get() && !overflowReported) {
                    overflowReported = true;
                    report.accept("卡頓紀錄佇列已滿，部分紀錄未寫入");
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException exception) {
            report.accept("卡頓紀錄寫入失敗，已停用記錄");
        } finally {
            deactivate();
        }
    }

    private synchronized void deactivate() {
        available.set(false);
        lines.clear();
    }

    @Override
    public void close() {
        deactivate();
        worker.interrupt();
    }
}
