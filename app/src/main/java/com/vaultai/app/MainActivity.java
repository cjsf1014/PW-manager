package com.vaultai.app;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int REQ_EXPORT = 1001;
    private static final int REQ_IMPORT = 1002;
    
    private LinearLayout passwordListContainer;
    private LinearLayout emptyStateLayout;
    private Button addPasswordButton;
    private Button bulkDeleteButton;
    private Button exportButton;
    private Button importButton;
    private android.widget.ImageButton overflowMenuButton;
    private EditText searchEditText;
    private PasswordManager passwordManager;
    private String masterPassword;
    private ClipboardManager clipboardManager;
    private List<String> allPasswords = new ArrayList<>();
    private boolean multiSelectMode = false;
    private java.util.Set<String> selectedKeys = new java.util.HashSet<>();
    private java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));
    private AlertDialog progressDialog;
    private Button selectAllButton;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            Log.d(TAG, "MainActivity onCreate started");
            try {
                SharedPreferences themePrefs = getSharedPreferences("VaultAIAuth", MODE_PRIVATE);
                String mode = themePrefs.getString("theme_mode", "light");
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                        "dark".equals(mode) ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                                : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
            } catch (Exception ignored) {}
            
            // 1. 尽早设置ContentView，避免黑屏或崩溃
            setContentView(R.layout.activity_main);
            Log.d(TAG, "setContentView completed");
            
            // 获取主密码
            Intent intent = getIntent();
            if (intent != null) {
                masterPassword = intent.getStringExtra("master_password");
                Log.d(TAG, "Intent received: " + (intent.getExtras() != null ? "has extras" : "no extras"));
                Log.d(TAG, "Master password retrieved: " + (TextUtils.isEmpty(masterPassword) ? "empty" : "present"));
                if (!TextUtils.isEmpty(masterPassword)) {
                    Log.d(TAG, "Master password length: " + masterPassword.length());
                }
            } else {
                Log.e(TAG, "Intent is null!");
                masterPassword = "";
            }
            
            if (TextUtils.isEmpty(masterPassword)) {
                Log.e(TAG, "Master password is empty, returning to login");
                SharedPreferences prefs = getSharedPreferences("VaultAIAuth", MODE_PRIVATE);
                prefs.edit().putBoolean("is_logged_in", false).apply();
                Intent loginIntent = new Intent(this, LoginActivity.class);
                startActivity(loginIntent);
                finish();
                return;
            }
            
            // 继续初始化
            continueInitialization();
            
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            showFatalErrorDialog("应用初始化失败: " + e.getMessage());
        }
    }

    private void showFatalErrorDialog(String message) {
        try {
            new AlertDialog.Builder(this)
                .setTitle("错误")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .setCancelable(false)
                .show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to show error dialog", e);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            
        }
    }
    
    private void continueInitialization() {
        try {
            Log.d(TAG, "Continuing initialization");
            
            Log.d(TAG, "Step 1: Initializing views...");
            initializeViews();
            Log.d(TAG, "Step 1: Views initialized successfully");
            
            Log.d(TAG, "Step 2: Setting up listeners...");
            setupListeners();
            Log.d(TAG, "Step 2: Listeners setup successfully");
            
            Log.d(TAG, "Step 3: Creating PasswordManager...");
            passwordManager = new PasswordManager(this);
            Log.d(TAG, "Step 3: PasswordManager created successfully");
            
            Log.d(TAG, "Step 4: Getting ClipboardManager...");
            clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboardManager == null) {
                Log.w(TAG, "ClipboardManager is null");
            } else {
                Log.d(TAG, "Step 4: ClipboardManager obtained successfully");
            }
            
            Log.d(TAG, "Step 5: Loading password list...");
            loadPasswordList();
            Log.d(TAG, "Step 5: Password list loaded successfully");
            
            Log.d(TAG, "All initialization steps completed successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error in continueInitialization at step: " + e.getMessage(), e);
            Toast.makeText(this, "初始化失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void initializeViews() {
        try {
            Log.d(TAG, "Initializing views...");
            
            passwordListContainer = findViewById(R.id.passwordListContainer);
            Log.d(TAG, "passwordListContainer: " + (passwordListContainer != null ? "found" : "null"));
            
            emptyStateLayout = findViewById(R.id.emptyStateLayout);
            Log.d(TAG, "emptyStateLayout: " + (emptyStateLayout != null ? "found" : "null"));
            
            addPasswordButton = findViewById(R.id.addPasswordButton);
            Log.d(TAG, "addPasswordButton: " + (addPasswordButton != null ? "found" : "null"));
            bulkDeleteButton = findViewById(R.id.bulkDeleteButton);
            Log.d(TAG, "bulkDeleteButton: " + (bulkDeleteButton != null ? "found" : "null"));
            selectAllButton = findViewById(R.id.selectAllButton);
            overflowMenuButton = findViewById(R.id.overflowMenuButton);
            
            searchEditText = findViewById(R.id.searchEditText);
            Log.d(TAG, "searchEditText: " + (searchEditText != null ? "found" : "null"));
            
            if (passwordListContainer == null || emptyStateLayout == null || addPasswordButton == null || overflowMenuButton == null) {
                String errorMsg = "关键视图未找到: " +
                    "passwordListContainer=" + (passwordListContainer != null ? "OK" : "MISSING") + ", " +
                    "emptyStateLayout=" + (emptyStateLayout != null ? "OK" : "MISSING") + ", " +
                    "addPasswordButton=" + (addPasswordButton != null ? "OK" : "MISSING") + ", " +
                    "overflowMenuButton=" + (overflowMenuButton != null ? "OK" : "MISSING");
                Log.e(TAG, errorMsg);
                throw new RuntimeException(errorMsg);
            }
            
            Log.d(TAG, "All views initialized successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views", e);
            throw e;
        }
    }
    
    private void setupListeners() {
        if (addPasswordButton != null) {
            addPasswordButton.setOnClickListener(v -> {
                if (multiSelectMode) {
                    cancelMultiSelectMode();
                } else {
                    showAddPasswordDialog();
                }
            });
        }

        if (bulkDeleteButton != null) {
            bulkDeleteButton.setOnClickListener(v -> onBulkDeleteClicked());
        }

        if (selectAllButton != null) {
            selectAllButton.setOnClickListener(v -> onSelectAllClicked());
        }


        if (overflowMenuButton != null) {
            overflowMenuButton.setOnClickListener(v -> showOverflowMenu(v));
        }
        
        if (searchEditText != null) {
            // Restore TextWatcher for real-time search
            searchEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterPasswords(s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
            
            searchEditText.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    filterPasswords(v.getText().toString());
                    return true;
                }
                return false;
            });
        }
    }
    
    private void loadPasswordList() {
        try {
            if (passwordManager == null) {
                return;
            }
            executor.execute(() -> {
                try {
                    List<String> list = passwordManager.getAllPasswords(masterPassword);
                    runOnUiThread(() -> {
                        allPasswords = list;
                        String query = searchEditText != null ? searchEditText.getText().toString() : "";
                        filterPasswords(query);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this, "加载密码列表失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            });
        } catch (Exception ignored) {}
    }

    private void filterPasswords(String query) {
        if (passwordListContainer != null) {
            passwordListContainer.removeAllViews();
        }
        
        List<String> filteredList = new ArrayList<>();
        if (TextUtils.isEmpty(query)) {
            filteredList.addAll(allPasswords);
        } else {
            String lowerQuery = query.toLowerCase();
            for (String passwordData : allPasswords) {
                if (passwordData.toLowerCase().contains(lowerQuery)) {
                    filteredList.add(passwordData);
                }
            }
        }
        
        if (filteredList.isEmpty()) {
            if (TextUtils.isEmpty(query) && allPasswords.isEmpty()) {
                showEmptyState(); // No passwords at all
            } else if (!allPasswords.isEmpty()) {
                // TODO: Maybe show a "No results found" state instead of generic empty state
                // For now, just show nothing or the empty state
                showEmptyState(); 
            } else {
                 showEmptyState();
            }
        } else {
            hideEmptyState();
            for (String passwordData : filteredList) {
                addPasswordCard(passwordData);
            }
        }
    }
    
    private void addPasswordCard(String passwordData) {
        try {
            // 解析密码数据
            String[] lines = passwordData.split("\n");
            if (lines.length < 3) return;
            
            String siteName = lines[0].replace("网站: ", "").trim();
            String username = lines[1].replace("用户名: ", "").trim();
            String password = lines[2].replace("密码: ", "").trim();
            
            // 解析备注 (向后兼容)
            String noteTemp = "";
            if (lines.length >= 4) {
                // 查找包含"备注: "的行，因为可能没有备注或者位置不对（虽然格式是固定的）
                // 这里假设格式固定为第4行
                String line3 = lines[3];
                if (line3.startsWith("备注: ")) {
                    noteTemp = line3.replace("备注: ", "").trim();
                }
            }
            final String note = noteTemp;
            
            // Inflate XML layout
            View cardView = LayoutInflater.from(this).inflate(R.layout.item_password_card, passwordListContainer, false);
            
            // Find Views
            TextView siteNameTextView = cardView.findViewById(R.id.siteNameTextView);
            TextView usernameTextView = cardView.findViewById(R.id.usernameTextView);
            TextView passwordTextView = cardView.findViewById(R.id.passwordTextView);
            LinearLayout noteContainer = cardView.findViewById(R.id.noteContainer);
            TextView noteTextView = cardView.findViewById(R.id.noteTextView);
            
            View copyButton = cardView.findViewById(R.id.copyButton);
            View togglePasswordButton = cardView.findViewById(R.id.togglePasswordButton);
            View editButton = cardView.findViewById(R.id.editButton);
            View deleteButton = cardView.findViewById(R.id.deleteButton);
            android.widget.CheckBox selectCheckBox = cardView.findViewById(R.id.selectCheckBox);
            
            if (siteNameTextView == null || usernameTextView == null || passwordTextView == null || 
                noteContainer == null || noteTextView == null) {
                Log.e(TAG, "Critical views missing in password card layout");
                return;
            }

            // Bind Data
            siteNameTextView.setText(siteName);
            usernameTextView.setText(username);
            passwordTextView.setText("••••••••"); // Default hidden
            
            if (!TextUtils.isEmpty(note)) {
                noteTextView.setText(note);
                noteContainer.setVisibility(View.VISIBLE);
            } else {
                noteContainer.setVisibility(View.GONE);
            }
            
            // Setup Listeners
            copyButton.setOnClickListener(v -> {
                copyToClipboard(password);
                Toast.makeText(this, "密码已复制", Toast.LENGTH_SHORT).show();
            });
            
            togglePasswordButton.setOnClickListener(v -> {
                String currentText = passwordTextView.getText().toString();
                if (currentText.contains("••••••••")) {
                    passwordTextView.setText(password);
                    if (togglePasswordButton instanceof android.widget.ImageButton) {
                         ((android.widget.ImageButton)togglePasswordButton).setImageResource(R.drawable.ic_visibility_off);
                    }
                } else {
                    passwordTextView.setText("••••••••");
                    if (togglePasswordButton instanceof android.widget.ImageButton) {
                        ((android.widget.ImageButton)togglePasswordButton).setImageResource(R.drawable.ic_visibility);
                    }
                }
            });
            
            final String compositeKey = new PasswordManager(this).buildKey(siteName, username);
            editButton.setOnClickListener(v -> {
                showEditPasswordDialog(siteName, username, password, note);
            });
            
            deleteButton.setOnClickListener(v -> {
                showDeleteConfirmationDialog(siteName, username);
            });

            if (selectCheckBox != null) {
                if (multiSelectMode) {
                    selectCheckBox.setVisibility(View.VISIBLE);
                    editButton.setVisibility(View.GONE);
                    deleteButton.setVisibility(View.GONE);
                } else {
                    selectCheckBox.setVisibility(View.GONE);
                    editButton.setVisibility(View.VISIBLE);
                    deleteButton.setVisibility(View.VISIBLE);
                }
                selectCheckBox.setChecked(selectedKeys.contains(compositeKey));
                selectCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        selectedKeys.add(compositeKey);
                    } else {
                        selectedKeys.remove(compositeKey);
                    }
                });
            }
            
            // Add to container
            if (passwordListContainer != null) {
                passwordListContainer.addView(cardView);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "添加密码卡片时出错", e);
        }
    }

    private void onBulkDeleteClicked() {
        if (!multiSelectMode) {
            multiSelectMode = true;
            selectedKeys.clear();
            if (bulkDeleteButton != null) {
                bulkDeleteButton.setText("删除已选");
            }
            addPasswordButton.setText("取消");
            if (selectAllButton != null) {
                selectAllButton.setVisibility(View.VISIBLE);
                selectAllButton.setText("全选");
            }
            filterPasswords(searchEditText != null ? searchEditText.getText().toString() : "");
            Toast.makeText(this, "请选择要删除的条目", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedKeys.isEmpty()) {
            Toast.makeText(this, "未选择任何条目", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("确认批量删除")
                .setMessage("确定删除所选的 " + selectedKeys.size() + " 个条目吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    int success = 0;
                    for (String key : selectedKeys) {
                        String[] parts = key.replace("password_", "").split("\\|", -1);
                        String site = parts.length > 0 ? parts[0] : "";
                        String user = parts.length > 1 ? parts[1] : "";
                        if (passwordManager.deletePasswordBySiteAndUser(site, user)) {
                            success++;
                        }
                    }
                    Toast.makeText(this, "已删除 " + success + " 个条目", Toast.LENGTH_SHORT).show();
                    cancelMultiSelectMode();
                    loadPasswordList();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void cancelMultiSelectMode() {
        multiSelectMode = false;
        selectedKeys.clear();
        if (bulkDeleteButton != null) {
            bulkDeleteButton.setText("批量删除");
        }
        addPasswordButton.setText("+ 添加");
        if (selectAllButton != null) {
            selectAllButton.setVisibility(View.GONE);
        }
        filterPasswords(searchEditText != null ? searchEditText.getText().toString() : "");
    }

    private void onSelectAllClicked() {
        try {
            String query = searchEditText != null ? searchEditText.getText().toString() : "";
            List<String> source = new ArrayList<>();
            if (TextUtils.isEmpty(query)) { source.addAll(allPasswords); } else {
                String lower = query.toLowerCase();
                for (String p : allPasswords) if (p.toLowerCase().contains(lower)) source.add(p);
            }
            if (source.isEmpty()) return;
            boolean allSelected = true;
            for (String p : source) {
                String key = computeCompositeKeyFrom(p);
                if (!selectedKeys.contains(key)) { allSelected = false; break; }
            }
            if (allSelected) {
                for (String p : source) selectedKeys.remove(computeCompositeKeyFrom(p));
                if (selectAllButton != null) selectAllButton.setText("全选");
            } else {
                for (String p : source) selectedKeys.add(computeCompositeKeyFrom(p));
                if (selectAllButton != null) selectAllButton.setText("全不选");
            }
            filterPasswords(query);
        } catch (Exception ignored) {}
    }

    private String computeCompositeKeyFrom(String passwordData) {
        try {
            String[] lines = passwordData.split("\n");
            String siteName = lines.length > 0 ? lines[0].replace("网站: ", "").trim() : "";
            String username = lines.length > 1 ? lines[1].replace("用户名: ", "").trim() : "";
            return new PasswordManager(this).buildKey(siteName, username);
        } catch (Exception e) { return ""; }
    }

    private void showChangeMasterPasswordDialog() {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("修改主密码");

            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_master_password, null);
            builder.setView(dialogView);

            EditText currentInput = dialogView.findViewById(R.id.currentMasterInput);
            EditText newInput = dialogView.findViewById(R.id.newMasterInput);
            EditText confirmInput = dialogView.findViewById(R.id.confirmMasterInput);

            builder.setPositiveButton("更新", (dialog, which) -> {
                String current = currentInput.getText().toString().trim();
                String newPwd = newInput.getText().toString().trim();
                String confirm = confirmInput.getText().toString().trim();

                if (TextUtils.isEmpty(current) || TextUtils.isEmpty(newPwd) || TextUtils.isEmpty(confirm)) {
                    Toast.makeText(this, "请填写所有字段", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!newPwd.equals(confirm)) {
                    Toast.makeText(this, "两次输入的新密码不一致", Toast.LENGTH_SHORT).show();
                    return;
                }

                SharedPreferences auth = getSharedPreferences("VaultAIAuth", MODE_PRIVATE);
                String storedHash = auth.getString("master_password_hash", null);
                if (storedHash == null) {
                    Toast.makeText(this, "尚未设置主密码", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!hashPassword(current).equals(storedHash)) {
                    Toast.makeText(this, "当前主密码错误", Toast.LENGTH_SHORT).show();
                    return;
                }

                boolean ok = passwordManager.reEncryptAll(current, newPwd);
                if (!ok) {
                    Toast.makeText(this, "重新加密数据失败", Toast.LENGTH_SHORT).show();
                    return;
                }

                auth.edit()
                        .putString("master_password_hash", hashPassword(newPwd))
                        .putString("master_password", newPwd)
                        .apply();
                masterPassword = newPwd;
                Toast.makeText(this, "主密码已更新", Toast.LENGTH_SHORT).show();
            });

            builder.setNegativeButton("取消", null);
            builder.setNeutralButton("忘记主密码，清除所有数据", (dialog, which) -> {
                new AlertDialog.Builder(this)
                        .setTitle("重置数据")
                        .setMessage("这将清除所有密码和登录信息，是否继续？")
                        .setPositiveButton("清除", (d, w) -> {
                            getSharedPreferences("VaultAIPasswords", MODE_PRIVATE).edit().clear().apply();
                            getSharedPreferences("VaultAIAuth", MODE_PRIVATE).edit().clear().apply();
                            Toast.makeText(this, "已清除所有数据", Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(this, LoginActivity.class);
                            startActivity(intent);
                            finish();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            });

            builder.show();
        } catch (Exception e) {
            Log.e(TAG, "显示修改主密码对话框失败", e);
            Toast.makeText(this, "无法显示修改主密码对话框", Toast.LENGTH_SHORT).show();
        }
    }

    private String hashPassword(String password) {
        return String.valueOf(password.hashCode());
    }

    private void exportPasswordsToUri(android.net.Uri uri) {
        try {
            final android.net.Uri furi = uri;
            final java.util.concurrent.atomic.AtomicInteger total = new java.util.concurrent.atomic.AtomicInteger();
            final java.util.concurrent.atomic.AtomicInteger exported = new java.util.concurrent.atomic.AtomicInteger();
            final java.util.concurrent.atomic.AtomicInteger failed = new java.util.concurrent.atomic.AtomicInteger();
            runAsync("正在导出...", () -> {
                try {
                    SharedPreferences prefs = getSharedPreferences("VaultAIPasswords", MODE_PRIVATE);
                    java.util.List<String> keys = new java.util.ArrayList<>();
                    for (String key : prefs.getAll().keySet()) {
                        if (key.startsWith("password_")) {
                            String enc = prefs.getString(key, null);
                            if (enc != null && !enc.trim().isEmpty()) keys.add(key);
                        }
                    }
                    java.util.List<org.json.JSONObject> list = new java.util.ArrayList<>(keys.size());
                    java.util.List<java.util.concurrent.Callable<org.json.JSONObject>> tasks = new java.util.ArrayList<>();
                    for (String key : keys) {
                        tasks.add(() -> {
                            try {
                                String enc = prefs.getString(key, null);
                                if (enc == null) return null;
                                total.incrementAndGet();
                                String plain = passwordManager.decryptData(enc, masterPassword);
                                String[] lines = plain.split("\n");
                                String siteName = lines[0].replace("网站: ", "").trim();
                                String username = lines[1].replace("用户名: ", "").trim();
                                String password = lines[2].replace("密码: ", "").trim();
                                String note = lines.length >= 4 && lines[3].startsWith("备注: ") ? lines[3].replace("备注: ", "").trim() : "";
                                org.json.JSONObject obj = new org.json.JSONObject();
                                obj.put("siteName", siteName);
                                obj.put("username", username);
                                obj.put("password", password);
                                obj.put("note", note);
                                exported.incrementAndGet();
                                return obj;
                            } catch (Exception e) {
                                failed.incrementAndGet();
                                return null;
                            }
                        });
                    }
                    java.util.List<java.util.concurrent.Future<org.json.JSONObject>> futures = ((java.util.concurrent.ExecutorService) executor).invokeAll(tasks);
                    for (java.util.concurrent.Future<org.json.JSONObject> f : futures) {
                        try {
                            org.json.JSONObject obj = f.get();
                            if (obj != null) list.add(obj);
                        } catch (Exception ignored) {}
                    }
                    org.json.JSONArray arr = new org.json.JSONArray();
                    for (org.json.JSONObject obj : list) arr.put(obj);
                    String json = arr.toString();
                    String cipherText = passwordManager.encryptForExportV2(json, masterPassword);
                    try (java.io.OutputStream os = getContentResolver().openOutputStream(furi, "w")) {
                        if (os == null) throw new java.io.IOException("openOutputStream returned null");
                        os.write(cipherText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                } catch (Exception ex) {
                }
            }, () -> Toast.makeText(this, "导出成功：总计 " + total.get() + "，成功 " + exported.get() + "，失败 " + failed.get(), Toast.LENGTH_LONG).show());
        } catch (Exception e) {
            Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void importPasswordsFromUri(android.net.Uri uri) {
        try {
            final android.net.Uri furi = uri;
            runAsync("正在读取...", () -> {
                try {
                    String cipherText;
                    try (java.io.InputStream is = getContentResolver().openInputStream(furi);
                         java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
                        if (is == null) throw new java.io.IOException("openInputStream returned null");
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = is.read(buf)) > 0) {
                            bos.write(buf, 0, n);
                        }
                        cipherText = new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
                    }
                    String json = passwordManager.decryptData(cipherText, masterPassword);
                    final org.json.JSONArray arr = new org.json.JSONArray(json);
                    runOnUiThread(() -> showImportPreviewDialog(arr));
                } catch (Exception ignored) {}
            }, null);
        } catch (Exception e) {
            Toast.makeText(this, "导入失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showImportPreviewDialog(org.json.JSONArray arr) {
        try {
            SharedPreferences prefs = getSharedPreferences("VaultAIPasswords", MODE_PRIVATE);
            int total = arr.length();
            java.util.Set<String> existing = new java.util.HashSet<>();
            for (String k : prefs.getAll().keySet()) if (k.startsWith("password_")) existing.add(k);
            int duplicates = 0;
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject obj = arr.getJSONObject(i);
                String siteName = obj.optString("siteName", "");
                String username = obj.optString("username", "");
                String key = new PasswordManager(this).buildKey(siteName, username);
                if (existing.contains(key)) {
                    duplicates++;
                }
            }

            new AlertDialog.Builder(this)
                .setTitle("导入预览")
                .setMessage("总计: " + total + "\n重复: " + duplicates + "\n选择导入策略：")
                .setPositiveButton("全部覆盖", (d, w) -> performImport(arr, true))
                .setNegativeButton("仅新增", (d, w) -> performImport(arr, false))
                .setNeutralButton("取消", null)
                .show();
        } catch (Exception e) {
            Log.e(TAG, "显示导入预览失败", e);
            Toast.makeText(this, "无法显示导入预览", Toast.LENGTH_SHORT).show();
        }
    }

    private void performImport(org.json.JSONArray arr, boolean overwrite) {
        try {
            final java.util.concurrent.atomic.AtomicInteger success = new java.util.concurrent.atomic.AtomicInteger();
            final java.util.concurrent.atomic.AtomicInteger skipped = new java.util.concurrent.atomic.AtomicInteger();
            final java.util.concurrent.atomic.AtomicInteger overwritten = new java.util.concurrent.atomic.AtomicInteger();
            runAsync("正在导入...", () -> {
                try {
                    SharedPreferences prefs = getSharedPreferences("VaultAIPasswords", MODE_PRIVATE);
                    java.util.Set<String> existing = new java.util.HashSet<>();
                    for (String k : prefs.getAll().keySet()) if (k.startsWith("password_")) existing.add(k);
                    List<PasswordEntry> toSave = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject obj = arr.getJSONObject(i);
                        String siteName = obj.optString("siteName", "");
                        String username = obj.optString("username", "");
                        String password = obj.optString("password", "");
                        String note = obj.optString("note", "");
                        if (TextUtils.isEmpty(siteName) || TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
                            continue;
                        }
                        String keyNew = new PasswordManager(this).buildKey(siteName, username);
                        boolean exists = existing.contains(keyNew);
                        if (exists && !overwrite) {
                            skipped.incrementAndGet();
                            continue;
                        }
                        if (exists && overwrite) {
                            new PasswordManager(this).deletePasswordBySiteAndUser(siteName, username);
                            overwritten.incrementAndGet();
                        }
                        toSave.add(new PasswordEntry(siteName, username, password, note));
                    }
                    if (!toSave.isEmpty()) {
                        if (passwordManager.savePasswordsBatch(masterPassword, toSave)) {
                            success.set(toSave.size());
                        }
                    }
                } catch (Exception ignored) {}
            }, () -> {
                Toast.makeText(this, "导入完成：成功 " + success.get() + "，覆盖 " + overwritten.get() + "，跳过 " + skipped.get(), Toast.LENGTH_LONG).show();
                loadPasswordList();
            });
        } catch (Exception e) {
            Toast.makeText(this, "导入失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void startExportFlow() {
        try {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            intent.setType("application/octet-stream");
            intent.putExtra(android.content.Intent.EXTRA_TITLE, "vault_export.dat");
            startActivityForResult(intent, REQ_EXPORT);
        } catch (Exception e) {
            Log.e(TAG, "启动导出失败", e);
            Toast.makeText(this, "无法启动导出", Toast.LENGTH_SHORT).show();
        }
    }

    private void startImportFlow() {
        try {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            intent.setType("application/octet-stream");
            startActivityForResult(intent, REQ_IMPORT);
        } catch (Exception e) {
            Log.e(TAG, "启动导入失败", e);
            Toast.makeText(this, "无法启动导入", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        android.net.Uri uri = data.getData();
        if (uri == null) return;
        if (requestCode == REQ_EXPORT) {
            exportPasswordsToUri(uri);
        } else if (requestCode == REQ_IMPORT) {
            importPasswordsFromUri(uri);
        }
    }
    
    private void copyToClipboard(String text) {
        try {
            ClipData clip = ClipData.newPlainText("password", text);
            clipboardManager.setPrimaryClip(clip);
        } catch (Exception e) {
            Log.e(TAG, "复制到剪贴板失败", e);
        }
    }
    
    private void showAddPasswordDialog() {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("添加新密码");
            
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_password, null);
            builder.setView(dialogView);
            
            EditText siteNameInput = dialogView.findViewById(R.id.siteNameInput);
            EditText usernameInput = dialogView.findViewById(R.id.usernameInput);
            EditText passwordInput = dialogView.findViewById(R.id.passwordInput);
            EditText noteInput = dialogView.findViewById(R.id.noteInput);
            
            builder.setPositiveButton("保存", (dialog, which) -> {
                String siteName = siteNameInput.getText().toString().trim();
                String username = usernameInput.getText().toString().trim();
                String password = passwordInput.getText().toString().trim();
                String note = noteInput.getText().toString().trim();
                
                if (TextUtils.isEmpty(siteName) || TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
                    Toast.makeText(this, "请填写所有字段", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                final boolean[] ok = new boolean[]{false};
                runAsync("正在保存...", () -> {
                    PasswordEntry entry = new PasswordEntry(siteName, username, password, note);
                    ok[0] = passwordManager.savePassword(masterPassword, entry);
                }, () -> {
                    if (ok[0]) {
                        Toast.makeText(this, "密码已保存", Toast.LENGTH_SHORT).show();
                        loadPasswordList();
                    } else {
                        Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
                    }
                });
            });
            
            builder.setNegativeButton("取消", null);
            builder.show();
            
        } catch (Exception e) {
            Log.e(TAG, "显示添加密码对话框失败", e);
            Toast.makeText(this, "无法显示添加对话框", Toast.LENGTH_SHORT).show();
        }
    }

    private void showEditPasswordDialog(String oldSiteName, String oldUsername, String oldPassword, String oldNote) {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("编辑密码");
            
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_password, null);
            builder.setView(dialogView);
            
            EditText siteNameInput = dialogView.findViewById(R.id.siteNameInput);
            EditText usernameInput = dialogView.findViewById(R.id.usernameInput);
            EditText passwordInput = dialogView.findViewById(R.id.passwordInput);
            EditText noteInput = dialogView.findViewById(R.id.noteInput);
            
            // 填充旧数据
            siteNameInput.setText(oldSiteName);
            usernameInput.setText(oldUsername);
            passwordInput.setText(oldPassword);
            if (oldNote != null) {
                noteInput.setText(oldNote);
            }
            
            // 如果修改了网站名称，需要删除旧的条目再添加新的，因为key是基于网站名称的
            // 为了简化，我们可以禁用网站名称修改，或者在保存时处理
            // 这里我们允许修改，并在保存时处理
            
            builder.setPositiveButton("更新", (dialog, which) -> {
                String newSiteName = siteNameInput.getText().toString().trim();
                String newUsername = usernameInput.getText().toString().trim();
                String newPassword = passwordInput.getText().toString().trim();
                String newNote = noteInput.getText().toString().trim();
                
                if (TextUtils.isEmpty(newSiteName) || TextUtils.isEmpty(newUsername) || TextUtils.isEmpty(newPassword)) {
                    Toast.makeText(this, "请填写所有字段", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // 如果网站或用户名变了，删除旧的
                if (!oldSiteName.equals(newSiteName) || !oldUsername.equals(newUsername)) {
                    passwordManager.deletePasswordBySiteAndUser(oldSiteName, oldUsername);
                }
                
                final boolean[] ok = new boolean[]{false};
                runAsync("正在更新...", () -> {
                    PasswordEntry entry = new PasswordEntry(newSiteName, newUsername, newPassword, newNote);
                    ok[0] = passwordManager.savePassword(masterPassword, entry);
                }, () -> {
                    if (ok[0]) {
                        Toast.makeText(this, "密码已更新", Toast.LENGTH_SHORT).show();
                        loadPasswordList();
                    } else {
                        Toast.makeText(this, "更新失败", Toast.LENGTH_SHORT).show();
                    }
                });
            });
            
            builder.setNegativeButton("取消", null);
            builder.show();
            
        } catch (Exception e) {
            Log.e(TAG, "显示编辑密码对话框失败", e);
            Toast.makeText(this, "无法显示编辑对话框", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showDeleteConfirmationDialog(String siteName, String username) {
        try {
            new AlertDialog.Builder(this)
                    .setTitle("确认删除")
                    .setMessage("确定要删除 " + siteName + " / " + username + " 的密码吗？")
                    .setPositiveButton("删除", (dialog, which) -> {
                        if (passwordManager.deletePasswordBySiteAndUser(siteName, username)) {
                            Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                            loadPasswordList();
                        } else {
                            Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "显示删除确认对话框失败", e);
        }
    }
    
    private void showEmptyState() {
        if (emptyStateLayout != null) {
            emptyStateLayout.setVisibility(View.VISIBLE);
        }
    }
    
    private void hideEmptyState() {
        if (emptyStateLayout != null) {
            emptyStateLayout.setVisibility(View.GONE);
        }
    }
    
    @Override
    public void onBackPressed() {
        try {
            // 返回登录页面
            getSharedPreferences("VaultAIAuth", MODE_PRIVATE)
                    .edit()
                    .putBoolean("is_logged_in", false)
                    .apply();
            
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "返回键处理失败", e);
            super.onBackPressed();
        }
    }

    private void showOverflowMenu(View anchor) {
        try {
            android.widget.PopupMenu menu = new android.widget.PopupMenu(this, anchor);
            menu.getMenu().add("导出").setOnMenuItemClickListener(item -> { startExportFlow(); return true; });
            menu.getMenu().add("导入").setOnMenuItemClickListener(item -> { startImportFlow(); return true; });
            menu.getMenu().add("切换主题").setOnMenuItemClickListener(item -> { toggleTheme(); return true; });
            menu.show();
        } catch (Exception e) {
            Log.e(TAG, "显示菜单失败", e);
        }
    }

    private void toggleTheme() {
        SharedPreferences prefs = getSharedPreferences("VaultAIAuth", MODE_PRIVATE);
        String mode = prefs.getString("theme_mode", "light");
        String next = "dark".equals(mode) ? "light" : "dark";
        prefs.edit().putString("theme_mode", next).apply();
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                "dark".equals(next) ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                        : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        recreate();
    }

    private View buildProgressView(String msg) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(32, 32, 32, 32);
        android.widget.ProgressBar bar = new android.widget.ProgressBar(this);
        TextView tv = new TextView(this);
        tv.setText(msg);
        tv.setPadding(24, 0, 0, 0);
        layout.addView(bar);
        layout.addView(tv);
        return layout;
    }

    private void runAsync(String msg, Runnable work, Runnable after) {
        try {
            progressDialog = new AlertDialog.Builder(this)
                    .setView(buildProgressView(msg))
                    .setCancelable(false)
                    .create();
            progressDialog.show();
        } catch (Exception ignored) {}
        executor.execute(() -> {
            try {
                work.run();
            } catch (Exception ignored) {
            }
            runOnUiThread(() -> {
                try { if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss(); } catch (Exception ignored) {}
                if (after != null) after.run();
            });
        });
    }
}
