package com.vaultai.app;

public class PasswordEntry {
    private String siteName;
    private String username;
    private String password;
    private String note;
    
    public PasswordEntry(String siteName, String username, String password) {
        this(siteName, username, password, "");
    }

    public PasswordEntry(String siteName, String username, String password, String note) {
        this.siteName = (siteName != null) ? siteName : "";
        this.username = (username != null) ? username : "";
        this.password = (password != null) ? password : "";
        this.note = (note != null) ? note : "";
    }
    
    public String getSiteName() {
        return siteName;
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getPassword() {
        return password;
    }

    public String getNote() {
        return note;
    }
    
    @Override
    public String toString() {
        return "网站: " + siteName + "\n" +
               "用户名: " + username + "\n" +
               "密码: " + password + "\n" +
               "备注: " + note + "\n" +
               "------------------------";
    }
}