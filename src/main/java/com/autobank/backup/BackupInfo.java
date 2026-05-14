package com.autobank.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Lightweight value object describing a discovered backup file.
 */
public class BackupInfo {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy  HH:mm:ss");

    public final Path          path;
    public final long          sizeBytes;
    public final LocalDateTime createdAt;
    public final String        method;   // "mysqldump" or "jdbc"

    private BackupInfo(Path path, long sizeBytes, LocalDateTime createdAt, String method) {
        this.path      = path;
        this.sizeBytes = sizeBytes;
        this.createdAt = createdAt;
        this.method    = method;
    }

    /** Builds a BackupInfo from a .sql.gz path, reading the .meta sidecar if present. */
    public static BackupInfo from(Path sqlGz) throws IOException {
        long size = Files.size(sqlGz);
        Instant mtime = Files.getLastModifiedTime(sqlGz).toInstant();
        LocalDateTime dt = LocalDateTime.ofInstant(mtime, ZoneId.systemDefault());
        String method = "unknown";

        // Try sidecar
        Path meta = Path.of(sqlGz.toString().replace(".sql.gz", ".meta"));
        if (Files.exists(meta)) {
            Properties p = new Properties();
            try (var in = Files.newInputStream(meta)) { p.load(in); }
            method = p.getProperty("backup.method", "unknown");
            String created = p.getProperty("backup.created", "");
            if (!created.isEmpty()) {
                try { dt = LocalDateTime.parse(created.split("\\.")[0],
                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
                } catch (Exception ignored) {}
            }
        }

        return new BackupInfo(sqlGz, size, dt, method);
    }

    public String getFileName() { return path.getFileName().toString(); }

    public String getDisplayDate() { return createdAt.format(DISPLAY_FMT); }

    public String getDisplaySize() {
        if (sizeBytes < 1024)       return sizeBytes + " B";
        if (sizeBytes < 1024 * 1024) return String.format("%.1f KB", sizeBytes / 1024.0);
        return String.format("%.2f MB", sizeBytes / (1024.0 * 1024));
    }

    @Override
    public String toString() {
        return getFileName() + "  [" + getDisplaySize() + "]  " + getDisplayDate();
    }
}
