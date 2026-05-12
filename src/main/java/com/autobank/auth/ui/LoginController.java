package com.autobank.auth.ui;

import com.autobank.auth.model.User;
import com.autobank.auth.model.UserSession;
import com.autobank.auth.service.AuthService;
import com.autobank.util.I18n;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.util.Optional;

public class LoginController {

    @FXML private Label subtitleLabel;
    @FXML private Label usernameLabel;
    @FXML private Label passwordLabel;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    @FXML private Button loginButton;

    private final AuthService authService = new AuthService();

    @FXML
    public void initialize() {
        refreshLabels();
        passwordField.setOnAction(e -> handleLogin());
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError(I18n.t("login.error.empty"));
            return;
        }

        loginButton.setDisable(true);
        errorLabel.setVisible(false);

        Optional<User> result = authService.authenticate(username, password);
        if (result.isPresent()) {
            UserSession.getInstance().setCurrentUser(result.get());
            openMainWindow();
        } else {
            showError(I18n.t("login.error.invalid"));
            passwordField.clear();
            loginButton.setDisable(false);
        }
    }

    @FXML private void switchToEnglish() { I18n.load("en"); refreshLabels(); }
    @FXML private void switchToMarathi() { I18n.load("mr"); refreshLabels(); }

    private void refreshLabels() {
        subtitleLabel.setText(I18n.t("app.subtitle"));
        usernameLabel.setText(I18n.t("login.username"));
        passwordLabel.setText(I18n.t("login.password"));
        loginButton.setText(I18n.t("login.button"));
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
    }

    private void openMainWindow() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
            Scene scene = new Scene(loader.load(), 1100, 700);
            scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
            Stage stage = (Stage) loginButton.getScene().getWindow();
            stage.setResizable(true);
            stage.setMinWidth(900);
            stage.setMinHeight(600);
            stage.setTitle("AutoBank — " + UserSession.getInstance().getCurrentUser().getUsername());
            stage.setScene(scene);
        } catch (Exception e) {
            showError("Window error: " + e.getMessage());
        }
    }
}
