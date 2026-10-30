package org.example.urireceiver;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.InputStream;
import java.security.MessageDigest;
import org.json.JSONObject;

/** Independent receiver; it never converts content URIs into filesystem paths. */
public final class ReceiveActivity extends Activity {
    private TextView output;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout body = new LinearLayout(this); body.setOrientation(1);
        Button retry = new Button(this); retry.setText("重新開啟收到的 URI");
        retry.setOnClickListener(v -> inspect()); body.addView(retry);
        output = new TextView(this); output.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this); scroll.addView(output); body.addView(scroll);
        setContentView(body); inspect();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); inspect();
    }
    private void inspect() {
        final Intent incoming = new Intent(getIntent());
        output.setText("讀取中…");
        new Thread(() -> {
            JSONObject report = new JSONObject();
            try {
                Uri uri = incoming.getData();
                report.put("sdk", android.os.Build.VERSION.SDK_INT);
                report.put("action", incoming.getAction());
                report.put("intentMime", incoming.getType());
                report.put("flags", incoming.getFlags());
                if (uri == null || !"content".equals(uri.getScheme()))
                    throw new IllegalArgumentException("Expected content URI");
                report.put("scheme", uri.getScheme());
                report.put("providerMime", getContentResolver().getType(uri));
                report.put("explicitReadGrant", checkUriPermission(uri, Process.myPid(),
                        Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION));
                ClipData clip = incoming.getClipData();
                report.put("clipItems", clip == null ? 0 : clip.getItemCount());
                report.put("clipMatchesData", clip != null && clip.getItemCount() == 1
                        && uri.equals(clip.getItemAt(0).getUri()));
                try (Cursor c = getContentResolver().query(uri,
                        new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                        null, null, null)) {
                    if (c == null || !c.moveToFirst()) throw new IllegalStateException("No metadata");
                    report.put("displayNamePresent", !c.isNull(0));
                    report.put("metadataBytes", c.getLong(1));
                }
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long count = 0; byte[] buffer = new byte[65536];
                byte[] header = new byte[8]; int headerBytes = 0;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new IllegalStateException("Null input stream");
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        if (headerBytes < 8) {
                            int take = Math.min(8 - headerBytes, n);
                            System.arraycopy(buffer, 0, header, headerBytes, take); headerBytes += take;
                        }
                        digest.update(buffer, 0, n); count += n;
                    }
                }
                boolean png = java.util.Arrays.equals(header,
                        new byte[] {(byte)137,80,78,71,13,10,26,10});
                report.put("pngSignature", png); report.put("streamBytes", count);
                StringBuilder hex = new StringBuilder();
                for (byte b : digest.digest()) hex.append(String.format(java.util.Locale.ROOT,"%02x",b & 255));
                report.put("sha256", hex.toString());
                BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(in, null, bounds);
                }
                report.put("width", bounds.outWidth); report.put("height", bounds.outHeight);
                if (!png || bounds.outWidth < 1 || bounds.outHeight < 1
                        || (long)bounds.outWidth * bounds.outHeight > 16000000L)
                    throw new IllegalStateException("Invalid or excessive test image");
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    Bitmap decoded = BitmapFactory.decodeStream(in);
                    if (decoded == null) throw new IllegalStateException("Full decode failed");
                    report.put("fullDecode", true); decoded.recycle();
                }
                report.put("result", "PASS_READ_AND_DECODE");
            } catch (Throwable error) {
                try { report.put("result", "FAIL"); report.put("error", error.getClass().getName()+": "+error.getMessage()); }
                catch (Exception ignored) {}
            }
            final String text = report.toString();
            Log.i("PngUriReceiver", text);
            runOnUiThread(() -> output.setText(text));
        }, "PngUriReadTest").start();
    }
}
