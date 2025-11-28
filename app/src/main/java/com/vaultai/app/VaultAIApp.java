package com.vaultai.app;

import android.app.Application;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class VaultAIApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                String content = System.currentTimeMillis() + "\n" + Log.getStackTraceString(e);
                File f = new File(getFilesDir(), "last_crash.txt");
                try (FileOutputStream out = new FileOutputStream(f, false)) {
                    out.write(content.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception ex) {
                Log.e("VaultAIApp", "Crash log write failed", ex);
            }
        });
    }
}
