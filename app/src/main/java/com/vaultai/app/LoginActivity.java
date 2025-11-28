package com.vaultai.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    private static final String PREFS_NAME = "VaultAIAuth";
    private static final String KEY_MASTER_PASSWORD_HASH = "master_password_hash";
    // 不再保存主密码明文，仅保存哈希
    
    private EditText masterPasswordEditText;
    private Button verifyButton;
    private TextView errorTextView;
    private SharedPreferences authPrefs;
    private Button changeMasterOnLoginButton;
    private Button resetAllDataButton;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.d(TAG, "LoginActivity onCreate started");
        
        try {
            try {
                SharedPreferences themePrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                String mode = themePrefs.getString("theme_mode", "light");
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                        "dark".equals(mode) ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                                : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
            } catch (Exception ignored) {}
            // 确保先设置 Content View，防止后续操作因无 View 而崩溃
            // 或者至少让用户看到界面
            setContentView(R.layout.activity_login);
            Log.d(TAG, "Login layout set");

            authPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            Log.d(TAG, "SharedPreferences initialized");
            
            // 始终显示登录界面，不自动跳过
            
            Log.d(TAG, "User not logged in, showing login screen");
            // setContentView 已经调用过了
            
            initializeViews();
            setupListeners();
            String storedHashInit = authPrefs.getString(KEY_MASTER_PASSWORD_HASH, null);
            if (storedHashInit == null) {
                verifyButton.setText("设置主密码并进入");
            } else {
                verifyButton.setText("验证并进入");
            }
            Log.d(TAG, "LoginActivity initialization completed");
            
        } catch (Exception e) {
            Log.e(TAG, "Error in LoginActivity onCreate", e);
            showFatalErrorDialog("登录界面初始化失败: " + e.getMessage());
        }
    }

    private void showFatalErrorDialog(String message) {
        try {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("启动错误")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .setCancelable(false)
                .show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to show error dialog", e);
            // Fallback to Toast
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            
        }
    }
    
    private void initializeViews() {
        masterPasswordEditText = findViewById(R.id.masterPasswordEditText);
        verifyButton = findViewById(R.id.verifyButton);
        errorTextView = findViewById(R.id.errorTextView);
        changeMasterOnLoginButton = findViewById(R.id.changeMasterOnLoginButton);
        resetAllDataButton = findViewById(R.id.resetAllDataButton);
    }
    
    private void setupListeners() {
        verifyButton.setOnClickListener(v -> verifyMasterPassword());
        if (changeMasterOnLoginButton != null) {
            changeMasterOnLoginButton.setOnClickListener(v -> showChangeMasterPasswordDialogOnLogin());
        }
        if (resetAllDataButton != null) {
            resetAllDataButton.setOnClickListener(v -> confirmResetAllData());
        }
    }

    private void showChangeMasterPasswordDialogOnLogin() {
        try {
            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
            builder.setTitle("修改主密码");

            android.view.View dialogView = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_change_master_password, null);
            builder.setView(dialogView);

            android.widget.EditText currentInput = dialogView.findViewById(R.id.currentMasterInput);
            android.widget.EditText newInput = dialogView.findViewById(R.id.newMasterInput);
            android.widget.EditText confirmInput = dialogView.findViewById(R.id.confirmMasterInput);

            builder.setPositiveButton("更新", (dialog, which) -> {
                String current = currentInput.getText().toString().trim();
                String newPwd = newInput.getText().toString().trim();
                String confirm = confirmInput.getText().toString().trim();

                if (android.text.TextUtils.isEmpty(current) || android.text.TextUtils.isEmpty(newPwd) || android.text.TextUtils.isEmpty(confirm)) {
                    android.widget.Toast.makeText(this, "请填写所有字段", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!newPwd.equals(confirm)) {
                    android.widget.Toast.makeText(this, "两次输入的新密码不一致", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }

                String storedHash = authPrefs.getString(KEY_MASTER_PASSWORD_HASH, null);
                if (storedHash == null) {
                    android.widget.Toast.makeText(this, "尚未设置主密码", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!hashPassword(current).equals(storedHash)) {
                    android.widget.Toast.makeText(this, "当前主密码错误", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }

                PasswordManager pm = new PasswordManager(this);
                boolean ok = pm.reEncryptAll(current, newPwd);
                if (!ok) {
                    android.widget.Toast.makeText(this, "重新加密数据失败", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }

                authPrefs.edit()
                        .putString(KEY_MASTER_PASSWORD_HASH, hashPassword(newPwd))
                        .apply();
                android.widget.Toast.makeText(this, "主密码已更新", android.widget.Toast.LENGTH_SHORT).show();
            });

            builder.setNegativeButton("取消", null);
            builder.setNeutralButton("忘记主密码，清除所有数据", (d, w) -> confirmResetAllData());

            builder.show();
        } catch (Exception e) {
            android.util.Log.e(TAG, "显示修改主密码对话框失败", e);
            android.widget.Toast.makeText(this, "无法显示修改主密码对话框", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmResetAllData() {
        try {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("重置数据")
                    .setMessage("忘记主密码后，所有已保存的密码无法找回。继续将清除所有密码和登录信息并重置主密码。是否继续？")
                    .setPositiveButton("清除", (dialog, which) -> {
                        getSharedPreferences("VaultAIPasswords", MODE_PRIVATE).edit().clear().apply();
                        getSharedPreferences("VaultAIAuth", MODE_PRIVATE).edit().clear().apply();
                        android.widget.Toast.makeText(this, "已清除所有数据", android.widget.Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } catch (Exception e) {
            android.util.Log.e(TAG, "显示重置数据确认失败", e);
        }
    }
    
    private void verifyMasterPassword() {
        String enteredPassword = masterPasswordEditText.getText().toString().trim();
        
        if (TextUtils.isEmpty(enteredPassword)) {
            showError("请输入主密码");
            return;
        }
        
        try {
            String storedHash = authPrefs.getString(KEY_MASTER_PASSWORD_HASH, null);
            
            if (storedHash == null) {
                // 首次使用，设置主密码
                setMasterPassword(enteredPassword);
            } else {
                // 验证主密码
                if (verifyPasswordHash(enteredPassword, storedHash)) {
                    setUserLoggedIn(true);
                    navigateToMainActivity(enteredPassword);
                } else {
                    showError("主密码错误");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "验证主密码时出错", e);
            showError("验证失败，请重试");
        }
    }
    
    private void setMasterPassword(String password) {
        try {
            String hash = hashPassword(password);
            authPrefs.edit().putString(KEY_MASTER_PASSWORD_HASH, hash).apply();
            setUserLoggedIn(true);
            Toast.makeText(this, "主密码设置成功！", Toast.LENGTH_SHORT).show();
            navigateToMainActivity(password);
        } catch (Exception e) {
            Log.e(TAG, "设置主密码时出错", e);
            showError("设置主密码失败");
        }
    }
    
    // 已移除保存主密码明文的逻辑
    
    private boolean verifyPasswordHash(String password, String storedHash) {
        try {
            String hash = hashPassword(password);
            return hash.equals(storedHash);
        } catch (Exception e) {
            Log.e(TAG, "验证密码哈希时出错", e);
            return false;
        }
    }
    
    private String hashPassword(String password) {
        return String.valueOf(password.hashCode());
    }
    
    private void setUserLoggedIn(boolean loggedIn) {
        authPrefs.edit().putBoolean("is_logged_in", loggedIn).apply();
    }
    
    private boolean isUserLoggedIn() {
        return authPrefs.getBoolean("is_logged_in", false);
    }
    
    private void showError(String message) {
        errorTextView.setText(message);
        errorTextView.setVisibility(View.VISIBLE);
    }
    
    private void navigateToMainActivity(String masterPassword) {
        Log.d(TAG, "navigateToMainActivity called with password: " + (TextUtils.isEmpty(masterPassword) ? "empty" : "present"));
        if (TextUtils.isEmpty(masterPassword)) {
            Log.w(TAG, "Master password empty, not navigating and resetting login state");
            setUserLoggedIn(false);
            return;
        }
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("master_password", masterPassword);
        Log.d(TAG, "Intent created with extra: master_password");
        startActivity(intent);
        finish();
    }
    
    @Override
    public void onBackPressed() {
        // 禁用返回键，防止绕过登录
        moveTaskToBack(true);
    }
}
