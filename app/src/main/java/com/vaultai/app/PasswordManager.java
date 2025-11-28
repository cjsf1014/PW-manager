package com.vaultai.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public class PasswordManager {
    private static final String PREFS_NAME = "VaultAIPasswords";
    private static final String KEY_PREFIX = "password_";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH_GCM = 12;
    private static final int PBKDF2_ITERATIONS = 120000;
    private static final int KEY_LENGTH_BITS = 128;
    
    private SharedPreferences sharedPreferences;
    private SecretKeySpec cachedMasterKey;
    private final Context appContext;
    
    public PasswordManager(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        try {
            this.appContext = context.getApplicationContext();
            this.sharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            if (this.sharedPreferences == null) {
                throw new RuntimeException("Failed to initialize SharedPreferences");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create PasswordManager: " + e.getMessage(), e);
        }
    }

    public boolean reEncryptAll(String oldMasterPassword, String newMasterPassword) {
        try {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            for (String key : sharedPreferences.getAll().keySet()) {
                if (!key.startsWith(KEY_PREFIX)) continue;
                String encrypted = sharedPreferences.getString(key, null);
                if (encrypted == null || encrypted.trim().isEmpty()) continue;
                try {
                    String plain = decryptAny(encrypted, oldMasterPassword);
                    String reenc = encryptV2(plain, newMasterPassword);
                    editor.putString(key, reenc);
                } catch (Exception e) {
                    // 如果某条目解密失败，则跳过该条目
                    e.printStackTrace();
                }
            }
            editor.apply();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public String encryptData(String data, String key) throws Exception {
        return encryptV3(data, key);
    }

    public String decryptData(String data, String key) throws Exception {
        return decryptAny(data, key);
    }

    public String encryptForExportV2(String data, String key) throws Exception {
        return encryptV2(data, key);
    }
    
    /**
     * 保存密码条目
     */
    public boolean savePassword(String masterPassword, PasswordEntry entry) {
        try {
            String encryptedData = encryptV3(entry.toString(), masterPassword);
            String key = buildKey(entry.getSiteName(), entry.getUsername());
            sharedPreferences.edit().putString(key, encryptedData).apply();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean savePasswordsBatch(String masterPassword, List<PasswordEntry> entries) {
        try {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            for (PasswordEntry entry : entries) {
                if (entry == null) continue;
                String encryptedData = encryptV3(entry.toString(), masterPassword);
                String key = buildKey(entry.getSiteName(), entry.getUsername());
                editor.putString(key, encryptedData);
            }
            editor.apply();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 获取所有保存的密码
     */
    public List<String> getAllPasswords(String masterPassword) {
        List<String> passwords = new ArrayList<>();
        
        for (String key : sharedPreferences.getAll().keySet()) {
            if (key.startsWith(KEY_PREFIX)) {
                String encryptedData = sharedPreferences.getString(key, "");
                // 检查是否为空字符串
                if (encryptedData == null || encryptedData.trim().isEmpty()) {
                    continue;
                }
                try {
                    String decryptedData = decryptAny(encryptedData, masterPassword);
                    passwords.add(decryptedData);
                } catch (Exception e) {
                    // 如果解密失败，可能是主密码错误
                    passwords.add("【解密失败】网站: " + key.replace(KEY_PREFIX, ""));
                }
            }
        }
        
        return passwords;
    }

    private String encryptV2(String data, String password) throws Exception {
        byte[] salt = new byte[SALT_LENGTH];
        byte[] iv = new byte[IV_LENGTH_GCM];
        SecureRandom random = new SecureRandom();
        random.nextBytes(salt);
        random.nextBytes(iv);
        SecretKeySpec secretKey = deriveKey(password, salt);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);
        byte[] encryptedData = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        byte[] combined = new byte[salt.length + iv.length + encryptedData.length];
        System.arraycopy(salt, 0, combined, 0, salt.length);
        System.arraycopy(iv, 0, combined, salt.length, iv.length);
        System.arraycopy(encryptedData, 0, combined, salt.length + iv.length, encryptedData.length);
        return "v2:" + Base64.encodeToString(combined, Base64.DEFAULT);
    }

    private String encryptV3(String data, String password) throws Exception {
        SecretKeySpec masterKey = getOrCreateMasterKey(password);
        byte[] iv = new byte[IV_LENGTH_GCM];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, masterKey, gcmSpec);
        byte[] encryptedData = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        byte[] combined = new byte[iv.length + encryptedData.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encryptedData, 0, combined, iv.length, encryptedData.length);
        return "v3:" + Base64.encodeToString(combined, Base64.DEFAULT);
    }

    private String decryptV2(String encryptedData, String password) throws Exception {
        byte[] combined = Base64.decode(encryptedData, Base64.DEFAULT);
        byte[] salt = new byte[SALT_LENGTH];
        byte[] iv = new byte[IV_LENGTH_GCM];
        System.arraycopy(combined, 0, salt, 0, salt.length);
        System.arraycopy(combined, salt.length, iv, 0, iv.length);
        byte[] enc = new byte[combined.length - salt.length - iv.length];
        System.arraycopy(combined, salt.length + iv.length, enc, 0, enc.length);
        SecretKeySpec secretKey = deriveKey(password, salt);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);
        byte[] decryptedData = cipher.doFinal(enc);
        return new String(decryptedData, StandardCharsets.UTF_8);
    }

    private String decryptLegacy(String encryptedData, String password) throws Exception {
        SecretKeySpec secretKey = generateKey(password);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
        byte[] combined = Base64.decode(encryptedData, Base64.DEFAULT);
        byte[] iv = new byte[16];
        System.arraycopy(combined, 0, iv, 0, iv.length);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        byte[] encryptedBytes = new byte[combined.length - iv.length];
        System.arraycopy(combined, iv.length, encryptedBytes, 0, encryptedBytes.length);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
        byte[] decryptedData = cipher.doFinal(encryptedBytes);
        return new String(decryptedData, StandardCharsets.UTF_8);
    }

    private String decryptAny(String stored, String password) throws Exception {
        if (stored != null) {
            if (stored.startsWith("v3:")) {
                return decryptV3(stored.substring(3), password);
            } else if (stored.startsWith("v2:")) {
                return decryptV2(stored.substring(3), password);
            }
        }
        return decryptLegacy(stored, password);
    }

    private String decryptV3(String encryptedData, String password) throws Exception {
        SecretKeySpec masterKey = getOrCreateMasterKey(password);
        byte[] combined = Base64.decode(encryptedData, Base64.DEFAULT);
        byte[] iv = new byte[IV_LENGTH_GCM];
        System.arraycopy(combined, 0, iv, 0, iv.length);
        byte[] enc = new byte[combined.length - iv.length];
        System.arraycopy(combined, iv.length, enc, 0, enc.length);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, masterKey, gcmSpec);
        byte[] decryptedData = cipher.doFinal(enc);
        return new String(decryptedData, StandardCharsets.UTF_8);
    }

    private SecretKeySpec getOrCreateMasterKey(String password) throws Exception {
        if (cachedMasterKey != null) return cachedMasterKey;
        SharedPreferences auth = appContext.getSharedPreferences("VaultAIAuth", Context.MODE_PRIVATE);
        String b64 = auth.getString("master_salt_b64", null);
        byte[] salt;
        if (b64 == null) {
            salt = new byte[SALT_LENGTH];
            new SecureRandom().nextBytes(salt);
            auth.edit().putString("master_salt_b64", Base64.encodeToString(salt, Base64.DEFAULT)).apply();
        } else {
            salt = Base64.decode(b64, Base64.DEFAULT);
        }
        cachedMasterKey = deriveKey(password, salt);
        return cachedMasterKey;
    }

    private SecretKeySpec deriveKey(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = skf.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, KEY_ALGORITHM);
    }
    
    /**
     * 删除指定网站的密码
     */
    public boolean deletePassword(String masterPassword, String siteName) {
        try {
            String key = KEY_PREFIX + siteName;
            if (sharedPreferences.contains(key)) {
                sharedPreferences.edit().remove(key).apply();
                return true;
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deletePasswordBySiteAndUser(String siteName, String username) {
        try {
            String newKey = buildKey(siteName, username);
            String legacyKey = KEY_PREFIX + siteName;
            SharedPreferences.Editor editor = sharedPreferences.edit();
            boolean removed = false;
            if (sharedPreferences.contains(newKey)) {
                editor.remove(newKey);
                removed = true;
            }
            if (sharedPreferences.contains(legacyKey)) {
                editor.remove(legacyKey);
                removed = true;
            }
            if (removed) {
                editor.apply();
            }
            return removed;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public String buildKey(String siteName, String username) {
        String s = siteName != null ? siteName : "";
        String u = username != null ? username : "";
        return KEY_PREFIX + s + "|" + u;
    }
    
    /**
     * 加密数据
     */
    private String encrypt(String data, String key) throws Exception {
        SecretKeySpec secretKey = generateKey(key);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
        
        // 生成随机IV
        byte[] iv = new byte[16];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
        byte[] encryptedData = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        
        // 将IV和加密数据一起返回
        byte[] combined = new byte[iv.length + encryptedData.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encryptedData, 0, combined, iv.length, encryptedData.length);
        
        return Base64.encodeToString(combined, Base64.DEFAULT);
    }
    
    /**
     * 解密数据
     */
    private String decrypt(String encryptedData, String key) throws Exception {
        SecretKeySpec secretKey = generateKey(key);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
        
        byte[] combined = Base64.decode(encryptedData, Base64.DEFAULT);
        
        // 提取IV
        byte[] iv = new byte[16];
        System.arraycopy(combined, 0, iv, 0, iv.length);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        
        // 提取加密数据
        byte[] encryptedBytes = new byte[combined.length - iv.length];
        System.arraycopy(combined, iv.length, encryptedBytes, 0, encryptedBytes.length);
        
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
        byte[] decryptedData = cipher.doFinal(encryptedBytes);
        return new String(decryptedData, StandardCharsets.UTF_8);
    }
    
    /**
     * 生成AES密钥
     */
    private SecretKeySpec generateKey(String password) throws Exception {
        // 简单的密钥生成 - 实际应用中应该使用更安全的密钥派生函数
        byte[] key = new byte[16]; // AES-128需要16字节密钥
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        
        // 将密码字节复制到密钥数组
        for (int i = 0; i < key.length && i < passwordBytes.length; i++) {
            key[i] = passwordBytes[i];
        }
        
        // 如果密码太短，用0填充
        for (int i = passwordBytes.length; i < key.length; i++) {
            key[i] = 0;
        }
        
        return new SecretKeySpec(key, KEY_ALGORITHM);
    }
}
