package com.autobank.auth.model;

public class UserSession {

    private static final UserSession INSTANCE = new UserSession();
    private User currentUser;

    private UserSession() {}

    public static UserSession getInstance() { return INSTANCE; }

    public void setCurrentUser(User user) { this.currentUser = user; }
    public User getCurrentUser() { return currentUser; }
    public boolean isLoggedIn() { return currentUser != null; }
    public void logout() { currentUser = null; }
}
