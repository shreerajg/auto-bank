package com.autobank.backup.ui;

import com.autobank.backup.BackupInfo;
import com.autobank.backup.BackupScheduler;
import com.autobank.backup.BackupService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class BackupController {

    @FXML private ListView<BackupInfo>  backupListView;
    @FXML private Label                 backupDirLabel;
    @FXML private Label                 statusLabel;
    @FXML private Button                backupNowBtn;
    @FXML private Button                restoreBtn;
    @FXML private Button                openDirBtn;
    @FXML private Label                 scheduledTimeLabel;
    @FXML private TextField             scheduleHourField;
    @FXML private TextField             scheduleMinuteField;

    private final BackupService   service   = new BackupService();
    private final BackupScheduler scheduler = BackupScheduler.getInstance();

    @FXML
    public void initialize() {
        backupDirLabel.setText(service.getBackupDir().toString());
        scheduledTimeLabel.setText(
            String.format("Auto-backup at: %s", scheduler.getScheduledTime()));
        scheduleHourField.setText(String.valueOf(scheduler.getScheduledTime().getHour()));
        scheduleMinuteField.setText(
            String.format("%02d", scheduler.getScheduledTime().getMinute()));

        // Custom cell: show filename + size + date
        backupListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(BackupInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText("📦  " + item.getFileName()
                        + "    " + item.getDisplaySize()
                        + "    " + item.getDisplayDate()
                        + "   [" + item.method + "]");
                    setStyle("-fx-text-fill: #d1d5db; -fx-font-family: 'Consolas', monospace; -fx-font-size: 12px;");
                }
            }
        });

        refreshList();
    }

    @FXML
    private void handleBackupNow() {
        setStatus("⏳ Creating backup…", false);
        backupNowBtn.setDisable(true);

        scheduler.triggerNow(
            path -> {
                setStatus("✅ Backup created: " + path.getFileName(), true);
                backupNowBtn.setDisable(false);
                refreshList();
            },
            err  -> {
                setStatus("❌ Backup failed: " + err, false);
                backupNowBtn.setDisable(false);
            }
        );
    }

    @FXML
    private void handleRestore() {
        BackupInfo selected = backupListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setStatus("⚠ Please select a backup to restore.", false);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Restore");
        confirm.setHeaderText("Restore database from backup?");
        confirm.setContentText(
            "This will OVERWRITE the current database with:\n" + selected.getFileName()
            + "\n\nThis cannot be undone. Proceed?");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                setStatus("⏳ Restoring from " + selected.getFileName() + "…", false);
                restoreBtn.setDisable(true);
                Thread t = new Thread(() -> {
                    try {
                        service.restoreBackup(selected.path);
                        javafx.application.Platform.runLater(() -> {
                            setStatus("✅ Restore complete!", true);
                            restoreBtn.setDisable(false);
                        });
                    } catch (Exception e) {
                        javafx.application.Platform.runLater(() -> {
                            setStatus("❌ Restore failed: " + e.getMessage(), false);
                            restoreBtn.setDisable(false);
                        });
                    }
                }, "restore-thread");
                t.setDaemon(true);
                t.start();
            }
        });
    }

    @FXML
    private void handleRestoreFromFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Backup File");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("AutoBank Backup (*.sql.gz)", "*.sql.gz"));
        fc.setInitialDirectory(service.getBackupDir().toFile());
        File file = fc.showOpenDialog(getStage());
        if (file == null) return;

        backupListView.getSelectionModel().clearSelection();
        // Reuse restore flow with a synthetic BackupInfo
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Restore from File");
        confirm.setHeaderText("Restore from external file?");
        confirm.setContentText("File: " + file.getName() + "\n\nOverwrite current database?");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            setStatus("⏳ Restoring…", false);
            Thread t = new Thread(() -> {
                try {
                    service.restoreBackup(file.toPath());
                    javafx.application.Platform.runLater(() ->
                        setStatus("✅ Restore complete!", true));
                } catch (Exception e) {
                    javafx.application.Platform.runLater(() ->
                        setStatus("❌ " + e.getMessage(), false));
                }
            }, "restore-ext-thread");
            t.setDaemon(true);
            t.start();
        });
    }

    @FXML
    private void handleDeleteSelected() {
        BackupInfo selected = backupListView.getSelectionModel().getSelectedItem();
        if (selected == null) { setStatus("Select a backup first.", false); return; }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Delete " + selected.getFileName() + "?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Delete backup?");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                try {
                    java.nio.file.Files.deleteIfExists(selected.path);
                    Path meta = Path.of(selected.path.toString().replace(".sql.gz", ".meta"));
                    java.nio.file.Files.deleteIfExists(meta);
                    setStatus("🗑 Deleted: " + selected.getFileName(), false);
                    refreshList();
                } catch (IOException e) {
                    setStatus("❌ Delete failed: " + e.getMessage(), false);
                }
            }
        });
    }

    @FXML
    private void handleOpenDir() {
        try {
            java.awt.Desktop.getDesktop().open(service.getBackupDir().toFile());
        } catch (Exception e) {
            setStatus("Cannot open folder: " + e.getMessage(), false);
        }
    }

    @FXML
    private void handleSaveSchedule() {
        try {
            int hour = Integer.parseInt(scheduleHourField.getText().trim());
            int min  = Integer.parseInt(scheduleMinuteField.getText().trim());
            if (hour < 0 || hour > 23 || min < 0 || min > 59)
                throw new IllegalArgumentException("Invalid time");
            java.time.LocalTime newTime = java.time.LocalTime.of(hour, min);
            scheduler.setScheduledTime(newTime);
            scheduler.start(
                p -> setStatus("✅ Scheduled backup done: " + p.getFileName(), true),
                e -> setStatus("❌ Scheduled backup failed: " + e, false)
            );
            scheduledTimeLabel.setText("Auto-backup at: " + newTime);
            setStatus("⏰ Schedule updated to " + newTime, true);
        } catch (Exception e) {
            setStatus("Invalid time — use HH and MM (0-23, 0-59).", false);
        }
    }

    @FXML
    private void handleRefresh() { refreshList(); }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void refreshList() {
        try {
            List<BackupInfo> list = service.listBackups();
            backupListView.setItems(FXCollections.observableArrayList(list));
            setStatus(list.isEmpty()
                ? "No backups yet — click 'Backup Now' to create one."
                : list.size() + " backup(s) found.", true);
        } catch (IOException e) {
            setStatus("Could not read backup directory: " + e.getMessage(), false);
        }
    }

    private void setStatus(String msg, boolean ok) {
        statusLabel.setText(msg);
        statusLabel.setStyle(ok
            ? "-fx-text-fill: #34d399; -fx-font-size: 12px;"
            : "-fx-text-fill: #f87171; -fx-font-size: 12px;");
    }

    private Stage getStage() {
        return (Stage) backupListView.getScene().getWindow();
    }
}
