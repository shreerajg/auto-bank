package com.autobank.ui;

import com.autobank.account.model.Account;
import com.autobank.account.service.AccountService;
import com.autobank.util.I18n;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CommandPalette extends StackPane {

    private final MainController mainController;
    private final TextField searchField;
    private final ListView<CommandItem> listView;
    private final List<CommandItem> allCommands = new ArrayList<>();
    private final ObservableList<CommandItem> filteredCommands = FXCollections.observableArrayList();
    private final AccountService accountService = new AccountService();

    public CommandPalette(MainController mainController) {
        this.mainController = mainController;

        getStyleClass().add("command-palette-overlay");
        setAlignment(Pos.TOP_CENTER);

        VBox palette = new VBox();
        palette.getStyleClass().add("command-palette");
        palette.setMaxWidth(600);
        palette.setMaxHeight(400);
        palette.setTranslateY(100);

        searchField = new TextField();
        searchField.getStyleClass().add("command-search-field");
        searchField.setPromptText("Type a command or search...");
        
        HBox searchBox = new HBox(searchField);
        searchBox.getStyleClass().add("command-search-box");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        listView = new ListView<>(filteredCommands);
        listView.getStyleClass().add("command-list");
        listView.setCellFactory(lv -> new CommandCell());
        VBox.setVgrow(listView, Priority.ALWAYS);

        palette.getChildren().addAll(searchBox, listView);
        getChildren().add(palette);

        initCommands();
        setupListeners();
        
        // Close on click outside
        this.setOnMouseClicked(e -> {
            if (e.getTarget() == this) {
                close();
            }
        });
    }

    private void initCommands() {
        allCommands.add(new CommandItem("📊", "Dashboard", "nav.dashboard", "Alt + 1", mainController::showDashboard));
        allCommands.add(new CommandItem("👤", "Accounts", "nav.accounts", "Alt + 2", mainController::showAccounts));
        allCommands.add(new CommandItem("💸", "Transactions", "nav.transactions", "Alt + 3", mainController::showTransactions));
        allCommands.add(new CommandItem("📈", "Interest Engine", "nav.interest", "Alt + 4", mainController::showInterest));
        allCommands.add(new CommandItem("🥛", "Distributions", "nav.distributions", "Alt + 5", mainController::showDistributions));
        allCommands.add(new CommandItem("🏠", "Loans", "nav.loans", "Alt + 6", mainController::showLoans));
        allCommands.add(new CommandItem("📅", "Daily Operations", "nav.dailyops", "Alt + 7", mainController::showDailyOps));
        allCommands.add(new CommandItem("📈", "Reports", "nav.reports", "Alt + 8", mainController::showReports));
        allCommands.add(new CommandItem("🔒", "Backup & Recovery", "nav.backup", "Alt + 9", mainController::showBackup));
        allCommands.add(new CommandItem("⚙", "Settings", "nav.settings", "Alt + 0", mainController::showSettings));
        allCommands.add(new CommandItem("🌐", "Toggle Language", "common.language", "Ctrl + L", mainController::toggleLanguage));
        allCommands.add(new CommandItem("⬅", "Logout", "common.logout", "", mainController::handleLogout));

        filteredCommands.addAll(allCommands);
    }

    private void setupListeners() {
        searchField.textProperty().addListener((obs, old, val) -> filter(val));

        searchField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.DOWN) {
                listView.getSelectionModel().selectNext();
                e.consume();
            } else if (e.getCode() == KeyCode.UP) {
                listView.getSelectionModel().selectPrevious();
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                executeSelected();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                close();
                e.consume();
            }
        });

        listView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1) {
                executeSelected();
            }
        });
    }

    private void filter(String query) {
        if (query == null || query.isBlank()) {
            filteredCommands.setAll(allCommands);
        } else {
            String q = query.toLowerCase();
            filteredCommands.setAll(allCommands.stream()
                .filter(c -> c.label.toLowerCase().contains(q) || I18n.t(c.i18nKey).toLowerCase().contains(q))
                .collect(Collectors.toList()));
        }
        listView.getSelectionModel().selectFirst();
    }

    private void executeSelected() {
        CommandItem selected = listView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            selected.action.run();
            close();
        }
    }

    public void show(StackPane parent) {
        parent.getChildren().add(this);
        Platform.runLater(searchField::requestFocus);
    }

    private void close() {
        if (getParent() instanceof StackPane) {
            ((StackPane) getParent()).getChildren().remove(this);
        }
    }

    private static class CommandItem {
        final String icon;
        final String label;
        final String i18nKey;
        final String shortcut;
        final Runnable action;

        CommandItem(String icon, String label, String i18nKey, String shortcut, Runnable action) {
            this.icon = icon;
            this.label = label;
            this.i18nKey = i18nKey;
            this.shortcut = shortcut;
            this.action = action;
        }
    }

    private static class CommandCell extends javafx.scene.control.ListCell<CommandItem> {
        @Override
        protected void updateItem(CommandItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
            } else {
                HBox box = new HBox(12);
                box.setAlignment(Pos.CENTER_LEFT);
                box.getStyleClass().add("command-item");

                Label icon = new Label(item.icon);
                icon.getStyleClass().add("command-icon");

                VBox textBox = new VBox(2);
                Label label = new Label(I18n.t(item.i18nKey));
                label.getStyleClass().add("command-label");
                Label sub = new Label(item.label);
                sub.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
                textBox.getChildren().addAll(label, sub);

                javafx.scene.layout.Pane spacer = new javafx.scene.layout.Pane();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                Label shortcut = new Label(item.shortcut);
                shortcut.getStyleClass().add("command-shortcut");

                box.getChildren().addAll(icon, textBox, spacer, shortcut);
                setGraphic(box);
                setText(null);
            }
        }
    }
}
