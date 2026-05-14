package com.autobank.backup;

import com.autobank.config.DatabaseConfig;
import com.autobank.util.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/**
 * BackupService — performs MySQL dump, compresses it, and manages rotation.
 *
 * Backup file format:  autobank_backup_YYYYMMDD_HHmmss.sql.gz
 * Default backup dir:  <user.home>/AutoBank/backups/
 * Retention policy:    keep last N backups (configurable, default 10)
 */
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final Path backupDir;
    private final int maxRetained;

    /** Default constructor — uses ~/AutoBank/backups, keeps 10 copies. */
    public BackupService() {
        this(
            Paths.get(System.getProperty("user.home"), "AutoBank", "backups"),
            10
        );
    }

    public BackupService(Path backupDir, int maxRetained) {
        this.backupDir   = backupDir;
        this.maxRetained = maxRetained;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Creates a full backup. Returns the path of the created .sql.gz file.
     * Throws IOException on any failure so the caller can surface it.
     */
    public Path createBackup() throws IOException {
        ensureBackupDirExists();

        String timestamp = LocalDateTime.now().format(FMT);
        Path outFile = backupDir.resolve("autobank_backup_" + timestamp + ".sql.gz");

        // 1. Read connection details from config
        DbCoords db = readDbCoords();

        // 2. Try mysqldump first; fall back to JDBC-based dump
        boolean dumpOk = false;
        if (isMysqldumpAvailable()) {
            try {
                runMysqldump(db, outFile);
                dumpOk = true;
                log.info("Backup via mysqldump: {}", outFile);
            } catch (Exception e) {
                log.warn("mysqldump failed ({}), falling back to JDBC dump", e.getMessage());
            }
        }

        if (!dumpOk) {
            jdbcDump(db, outFile);
            log.info("Backup via JDBC dump: {}", outFile);
        }

        // 3. Write metadata sidecar
        writeMetadata(outFile, db.database, dumpOk ? "mysqldump" : "jdbc");

        // 4. Rotate old backups
        rotate();

        // 5. Audit log
        AuditLogger.log("BACKUP_CREATED", "SYSTEM", null,
                "Backup: " + outFile.getFileName(), null);

        return outFile;
    }

    /**
     * Restores the database from a given .sql.gz backup file.
     * This imports the SQL into the configured database via a JDBC connection.
     */
    public void restoreBackup(Path backupFile) throws IOException {
        if (!Files.exists(backupFile))
            throw new FileNotFoundException("Backup file not found: " + backupFile);

        log.info("Restoring from {}", backupFile);
        DbCoords db = readDbCoords();

        // Read and decompress
        StringBuilder sql = new StringBuilder();
        try (InputStream fis   = Files.newInputStream(backupFile);
             InputStream gzip  = new java.util.zip.GZIPInputStream(fis);
             BufferedReader br = new BufferedReader(new InputStreamReader(gzip))) {
            String line;
            while ((line = br.readLine()) != null) {
                sql.append(line).append('\n');
            }
        }

        // Execute each statement via JDBC
        try (Connection conn = DatabaseConfig.getConnection();
             Statement  st   = conn.createStatement()) {
            conn.setAutoCommit(false);
            try {
                for (String stmt : splitSql(sql.toString())) {
                    String trimmed = stmt.strip();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                        st.execute(trimmed);
                    }
                }
                conn.commit();
                AuditLogger.log("BACKUP_RESTORED", "SYSTEM", null,
                        "Restored from: " + backupFile.getFileName(), null);
                log.info("Restore complete");
            } catch (Exception e) {
                conn.rollback();
                throw new IOException("Restore failed — rolled back: " + e.getMessage(), e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (IOException ioe) {
            throw ioe;
        } catch (Exception e) {
            throw new IOException("DB connection error during restore: " + e.getMessage(), e);
        }
    }

    /** Lists all backup files in the backup directory, newest first. */
    public List<BackupInfo> listBackups() throws IOException {
        ensureBackupDirExists();
        List<BackupInfo> list = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(backupDir, "autobank_backup_*.sql.gz")) {
            for (Path p : ds) {
                list.add(BackupInfo.from(p));
            }
        }
        list.sort(Comparator.comparing(b -> b.createdAt, Comparator.reverseOrder()));
        return list;
    }

    public Path getBackupDir() { return backupDir; }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private void ensureBackupDirExists() throws IOException {
        Files.createDirectories(backupDir);
    }

    /** Invokes mysqldump as an external process and gzip-compresses its output. */
    private void runMysqldump(DbCoords db, Path outFile) throws IOException, InterruptedException {
        // Build command — password via env variable to avoid shell history exposure
        List<String> cmd = new ArrayList<>();
        cmd.add("mysqldump");
        cmd.add("--host=" + db.host);
        cmd.add("--port=" + db.port);
        cmd.add("--user=" + db.user);
        cmd.add("--single-transaction");
        cmd.add("--routines");
        cmd.add("--triggers");
        cmd.add("--add-drop-table");
        cmd.add("--complete-insert");
        cmd.add(db.database);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("MYSQL_PWD", db.password);  // safer than --password flag
        pb.redirectErrorStream(false);
        Process proc = pb.start();

        // Stream stdout → gzip file
        try (InputStream stdout        = proc.getInputStream();
             OutputStream fos          = Files.newOutputStream(outFile);
             GZIPOutputStream gzipOut  = new GZIPOutputStream(fos)) {
            stdout.transferTo(gzipOut);
        }

        int exit = proc.waitFor();
        if (exit != 0) {
            String errMsg = new String(proc.getErrorStream().readAllBytes());
            Files.deleteIfExists(outFile);
            throw new IOException("mysqldump exited " + exit + ": " + errMsg);
        }
    }

    /**
     * JDBC-based dump: SELECT * from each table and write INSERT statements.
     * Covers all user-defined tables. No stored procedures/views.
     */
    private void jdbcDump(DbCoords db, Path outFile) throws IOException {
        try (Connection conn = DatabaseConfig.getConnection();
             OutputStream fos        = Files.newOutputStream(outFile);
             GZIPOutputStream gzipOut = new GZIPOutputStream(fos);
             PrintWriter pw          = new PrintWriter(new OutputStreamWriter(gzipOut))) {

            pw.println("-- AutoBank JDBC Backup  " + LocalDateTime.now());
            pw.println("-- Database: " + db.database);
            pw.println("SET FOREIGN_KEY_CHECKS=0;");
            pw.println();

            // Discover tables
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = conn.getMetaData().getTables(
                    db.database, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) tables.add(rs.getString("TABLE_NAME"));
            }

            for (String table : tables) {
                pw.println("-- Table: " + table);
                pw.println("TRUNCATE TABLE `" + table + "`;");

                try (Statement st   = conn.createStatement();
                     ResultSet rows = st.executeQuery("SELECT * FROM `" + table + "`")) {
                    int colCount = rows.getMetaData().getColumnCount();
                    while (rows.next()) {
                        StringBuilder ins = new StringBuilder("INSERT INTO `")
                                .append(table).append("` VALUES (");
                        for (int i = 1; i <= colCount; i++) {
                            Object val = rows.getObject(i);
                            if (val == null) {
                                ins.append("NULL");
                            } else {
                                ins.append("'")
                                   .append(val.toString().replace("'", "\\'"))
                                   .append("'");
                            }
                            if (i < colCount) ins.append(",");
                        }
                        ins.append(");");
                        pw.println(ins);
                    }
                } catch (Exception e) {
                    pw.println("-- ERROR dumping table " + table + ": " + e.getMessage());
                }
                pw.println();
            }

            pw.println("SET FOREIGN_KEY_CHECKS=1;");

        } catch (Exception e) {
            Files.deleteIfExists(outFile);
            throw new IOException("JDBC dump failed: " + e.getMessage(), e);
        }
    }

    /** Writes a small .meta file next to the backup with summary info. */
    private void writeMetadata(Path backup, String dbName, String method) {
        Path meta = Path.of(backup.toString().replace(".sql.gz", ".meta"));
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(meta))) {
            pw.println("backup.file=" + backup.getFileName());
            pw.println("backup.database=" + dbName);
            pw.println("backup.method=" + method);
            pw.println("backup.created=" + LocalDateTime.now());
            pw.println("backup.size_bytes=" + Files.size(backup));
        } catch (IOException e) {
            log.warn("Could not write metadata for {}", backup);
        }
    }

    /** Deletes oldest backups beyond maxRetained. */
    private void rotate() throws IOException {
        List<BackupInfo> all = listBackups();
        if (all.size() > maxRetained) {
            List<BackupInfo> toDelete = all.subList(maxRetained, all.size());
            for (BackupInfo bi : toDelete) {
                Files.deleteIfExists(bi.path);
                Files.deleteIfExists(Path.of(bi.path.toString().replace(".sql.gz", ".meta")));
                log.info("Rotated old backup: {}", bi.path.getFileName());
            }
        }
    }

    private boolean isMysqldumpAvailable() {
        try {
            Process p = new ProcessBuilder("mysqldump", "--version").start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Splits a SQL file into individual statements on ';'. */
    private List<String> splitSql(String sql) {
        List<String> stmts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inString = false;
        char strChar = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (inString) {
                cur.append(c);
                if (c == strChar && (i == 0 || sql.charAt(i - 1) != '\\')) inString = false;
            } else if (c == '\'' || c == '"') {
                inString = true;
                strChar = c;
                cur.append(c);
            } else if (c == ';') {
                stmts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (!cur.isEmpty()) stmts.add(cur.toString());
        return stmts;
    }

    // ── Inner: DB coords ─────────────────────────────────────────────────────

    private static DbCoords readDbCoords() {
        Properties props = new Properties();
        try (InputStream in = BackupService.class.getResourceAsStream("/config.properties")) {
            if (in != null) props.load(in);
        } catch (IOException ignored) {}
        String url      = props.getProperty("db.url", "jdbc:mysql://localhost:3306/autobank");
        String user     = props.getProperty("db.user", "root");
        String password = props.getProperty("db.password", "");
        // Parse host, port, dbname from jdbc URL
        // jdbc:mysql://host:port/dbname
        String host = "localhost";
        String port = "3306";
        String dbName = "autobank";
        try {
            String noPrefix = url.replace("jdbc:mysql://", "");
            String[] parts  = noPrefix.split("/");
            dbName = parts.length > 1 ? parts[1].split("\\?")[0] : dbName;
            String[] hp = parts[0].split(":");
            host = hp[0];
            if (hp.length > 1) port = hp[1];
        } catch (Exception ignored) {}
        return new DbCoords(host, port, dbName, user, password);
    }

    private record DbCoords(String host, String port, String database, String user, String password) {}
}
