package ir.scicore.flash.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import ir.scicore.flash.R;

/** راهنمای گام‌به‌گام ساخت فلش‌کارت با هوش مصنوعی (DeepSeek) + پراپمت‌های قابل کپی */
public class AiGuideActivity extends Base {

    private static final String DEEPSEEK_URL = "https://chat.deepseek.com/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_guide);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnOpenDeepseek).setOnClickListener(v -> openDeepseek());

        findViewById(R.id.btnCopy1).setOnClickListener(v ->
                copyPrompt(getString(R.string.ai_prompt1)));
        findViewById(R.id.btnCopy2).setOnClickListener(v ->
                copyPrompt(getString(R.string.ai_prompt2)));
    }

    private void openDeepseek() {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(DEEPSEEK_URL));
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, R.string.import_err, Toast.LENGTH_SHORT).show();
        }
    }

    private void copyPrompt(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("NeuAnki Prompt", text));
                Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception ignored) {
        }
    }
}
