package app.notron.notes;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // must be registered BEFORE super.onCreate so the bridge knows about it
        registerPlugin(NotronFSPlugin.class);
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().setOverScrollMode(View.OVER_SCROLL_NEVER);
            // an app should not pinch-zoom like a web page
            WebSettings ws = getBridge().getWebView().getSettings();
            ws.setSupportZoom(false);
            ws.setBuiltInZoomControls(false);
            ws.setDisplayZoomControls(false);
        }
        hideSystemBars();
        captureShare(getIntent(), false);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        captureShare(intent, true);
    }

    /** Text / links shared from other apps (Share > Notron) are kept for the web app to pick up. */
    private void captureShare(Intent intent, boolean live) {
        try {
            if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text == null || text.length() == 0) return;
            String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
            NotronFSPlugin.pendingSubject = subject == null ? "" : subject;
            NotronFSPlugin.pendingText = text;
            intent.setAction(Intent.ACTION_MAIN);
            if (live && getBridge() != null && getBridge().getWebView() != null) {
                final android.webkit.WebView wv = getBridge().getWebView();
                wv.post(new Runnable() {
                    @Override
                    public void run() {
                        wv.evaluateJavascript("window.__shared&&window.__shared()", null);
                    }
                });
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    private void hideSystemBars() {
        WindowInsetsControllerCompat controller =
            WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
    }
}
