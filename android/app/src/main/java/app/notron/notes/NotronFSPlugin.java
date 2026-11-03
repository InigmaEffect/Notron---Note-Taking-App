package app.notron.notes;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Base64;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;

import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Lets the web app use a real phone folder as a vault.
 * Uses Android's Storage Access Framework (the system folder picker), so no
 * storage permissions are needed and access survives app restarts.
 *
 * Every call carries "tree" (the picked folder's tree URI) and a document id.
 */
@CapacitorPlugin(name = "NotronFS")
public class NotronFSPlugin extends Plugin {

    private static final String[] COLS = new String[] {
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    };

    private ContentResolver cr() {
        return getContext().getContentResolver();
    }

    private static JSObject entryOf(Cursor c) {
        JSObject o = new JSObject();
        o.put("id", c.getString(0));
        o.put("name", c.getString(1));
        String mime = c.isNull(2) ? "" : c.getString(2);
        o.put("dir", DocumentsContract.Document.MIME_TYPE_DIR.equals(mime));
        o.put("size", c.isNull(3) ? 0L : c.getLong(3));
        o.put("mt", c.isNull(4) ? 0L : c.getLong(4));
        return o;
    }

    private JSObject statDoc(Uri tree, String docId) throws Exception {
        Uri doc = DocumentsContract.buildDocumentUriUsingTree(tree, docId);
        Cursor c = cr().query(doc, COLS, null, null, null);
        if (c == null) throw new Exception("Cannot read that item");
        try {
            if (!c.moveToFirst()) throw new Exception("Item not found");
            return entryOf(c);
        } finally {
            c.close();
        }
    }

    /* ---------- keyboard ---------- */

    /** Android WebView sometimes focuses a text field without raising the keyboard; this asks for it explicitly. */
    @PluginMethod
    public void showKeyboard(PluginCall call) {
        final WebView wv = getBridge().getWebView();
        if (wv != null) {
            wv.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        wv.requestFocus();
                        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) imm.showSoftInput(wv, InputMethodManager.SHOW_IMPLICIT);
                    } catch (Exception ignored) {
                    }
                }
            });
        }
        call.resolve();
    }

    /* ---------- pick a folder ---------- */

    @PluginMethod
    public void pickFolder(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        );
        startActivityForResult(call, i, "pickFolderResult");
    }

    @ActivityCallback
    private void pickFolderResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        try {
            Intent data = result.getData();
            if (result.getResultCode() != Activity.RESULT_OK || data == null || data.getData() == null) {
                call.reject("cancelled", "CANCELLED");
                return;
            }
            Uri tree = data.getData();
            cr().takePersistableUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );
            String rootId = DocumentsContract.getTreeDocumentId(tree);
            JSObject info = statDoc(tree, rootId);
            JSObject o = new JSObject();
            o.put("tree", tree.toString());
            o.put("doc", rootId);
            o.put("name", info.getString("name"));
            call.resolve(o);
        } catch (Exception e) {
            call.reject("Could not use that folder: " + e.getMessage());
        }
    }

    /* ---------- is the saved folder still allowed? ---------- */

    @PluginMethod
    public void check(PluginCall call) {
        String tree = call.getString("tree");
        boolean ok = false;
        if (tree != null) {
            List<UriPermission> perms = cr().getPersistedUriPermissions();
            for (UriPermission p : perms) {
                if (p.getUri().toString().equals(tree) && p.isReadPermission() && p.isWritePermission()) {
                    ok = true;
                    break;
                }
            }
        }
        JSObject o = new JSObject();
        o.put("granted", ok);
        call.resolve(o);
    }

    /* ---------- list / find / create ---------- */

    @PluginMethod
    public void list(PluginCall call) {
        try {
            Uri tree = Uri.parse(call.getString("tree"));
            String doc = call.getString("doc");
            Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, doc);
            JSArray arr = new JSArray();
            Cursor c = cr().query(kids, COLS, null, null, null);
            if (c != null) {
                try {
                    while (c.moveToNext()) arr.put(entryOf(c));
                } finally {
                    c.close();
                }
            }
            JSObject o = new JSObject();
            o.put("entries", arr);
            call.resolve(o);
        } catch (Exception e) {
            call.reject("list failed: " + e.getMessage());
        }
    }

    @PluginMethod
    public void child(PluginCall call) {
        try {
            Uri tree = Uri.parse(call.getString("tree"));
            String parent = call.getString("parent");
            String name = call.getString("name");
            boolean create = Boolean.TRUE.equals(call.getBoolean("create", false));
            boolean dir = Boolean.TRUE.equals(call.getBoolean("dir", false));

            Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent);
            Cursor c = cr().query(kids, COLS, null, null, null);
            if (c != null) {
                try {
                    while (c.moveToNext()) {
                        if (name.equals(c.getString(1))) {
                            call.resolve(entryOf(c));
                            return;
                        }
                    }
                } finally {
                    c.close();
                }
            }
            if (!create) {
                JSObject o = new JSObject();
                o.put("missing", true);
                call.resolve(o);
                return;
            }
            Uri parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, parent);
            // octet-stream stops Android from appending its own extension (e.g. note.md.txt)
            String mime = dir ? DocumentsContract.Document.MIME_TYPE_DIR : "application/octet-stream";
            Uri made = DocumentsContract.createDocument(cr(), parentUri, mime, name);
            if (made == null) {
                call.reject("Android refused to create \"" + name + "\"");
                return;
            }
            call.resolve(statDoc(tree, DocumentsContract.getDocumentId(made)));
        } catch (Exception e) {
            call.reject("child failed: " + e.getMessage());
        }
    }

    @PluginMethod
    public void stat(PluginCall call) {
        try {
            Uri tree = Uri.parse(call.getString("tree"));
            call.resolve(statDoc(tree, call.getString("doc")));
        } catch (Exception e) {
            call.reject("stat failed: " + e.getMessage());
        }
    }

    /* ---------- fast paths for big vaults ---------- */

    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);

    private String readText(Uri tree, String docId) throws Exception {
        Uri doc = DocumentsContract.buildDocumentUriUsingTree(tree, docId);
        InputStream in = cr().openInputStream(doc);
        if (in == null) throw new Exception("open failed");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        } finally {
            in.close();
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Lists the whole folder tree in ONE call (skips folders starting with a dot). */
    @PluginMethod
    public void walk(PluginCall call) {
        try {
            Uri tree = Uri.parse(call.getString("tree"));
            String root = call.getString("doc");
            JSArray arr = new JSArray();
            ArrayDeque<String> queue = new ArrayDeque<String>();
            queue.add(root);
            while (!queue.isEmpty()) {
                String par = queue.poll();
                Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, par);
                Cursor c = cr().query(kids, COLS, null, null, null);
                if (c == null) continue;
                try {
                    while (c.moveToNext()) {
                        JSObject e = entryOf(c);
                        e.put("par", par);
                        arr.put(e);
                        String nm = e.getString("name");
                        if (e.getBoolean("dir") && nm != null && !nm.startsWith(".")) {
                            queue.add(e.getString("id"));
                        }
                    }
                } finally {
                    c.close();
                }
            }
            JSObject o = new JSObject();
            o.put("entries", arr);
            call.resolve(o);
        } catch (Exception e) {
            call.reject("walk failed: " + e.getMessage());
        }
    }

    /** Reads many text files in parallel and returns them in the same order (null for failures). */
    @PluginMethod
    public void readBatch(PluginCall call) {
        try {
            final Uri tree = Uri.parse(call.getString("tree"));
            JSArray docs = call.getArray("docs");
            final int n = docs.length();
            final String[] ids = new String[n];
            for (int i = 0; i < n; i++) ids[i] = docs.getString(i);
            final String[] out = new String[n];
            ArrayList<Future<?>> jobs = new ArrayList<Future<?>>();
            for (int i = 0; i < n; i++) {
                final int k = i;
                jobs.add(POOL.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            out[k] = readText(tree, ids[k]);
                        } catch (Exception e) {
                            out[k] = null;
                        }
                    }
                }));
            }
            for (Future<?> f : jobs) f.get();
            JSArray arr = new JSArray();
            for (int i = 0; i < n; i++) arr.put(out[i] == null ? JSONObject.NULL : out[i]);
            JSObject o = new JSObject();
            o.put("texts", arr);
            call.resolve(o);
        } catch (Exception e) {
            call.reject("readBatch failed: " + e.getMessage());
        }
    }

    /* ---------- read / write / delete ---------- */

    @PluginMethod
    public void read(PluginCall call) {
        try {
            Uri tree = Uri.parse(call.getString("tree"));
            Uri doc = DocumentsContract.buildDocumentUriUsingTree(tree, call.getString("doc"));
            boolean asText = Boolean.TRUE.equals(call.getBoolean("text", false));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            InputStream in = cr().openInputStream(doc);
            if (in == null) {
                call.reject("Cannot open file");
                return;
            }
            try {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            } finally {
                in.close();
            }
            byte[] bytes = bos.toByteArray();
            JSObject o = new JSObject();
            if (asText) {
                o.put("text", new String(bytes, StandardCharsets.UTF_8));
            } else {
                o.put("data", Base64.encodeToString(bytes, Base64.NO_WRAP));
            }
            call.resolve(o);
        } catch (Exception e) {
            call.reject("read failed: " + e.getMessage());
        }
    }

    @PluginMethod
    public void write(PluginCall call) {
        try {
            Uri tree = Uri.parse(call.getString("tree"));
            String docId = call.getString("doc");
            Uri doc = DocumentsContract.buildDocumentUriUsingTree(tree, docId);
            String text = call.getString("text");
            byte[] bytes;
            if (text != null) {
                bytes = text.getBytes(StandardCharsets.UTF_8);
            } else {
                String b64 = call.getString("data", "");
                bytes = Base64.decode(b64, Base64.DEFAULT);
            }
            OutputStream out = cr().openOutputStream(doc, "wt");
            if (out == null) {
                call.reject("Cannot open file for writing");
                return;
            }
            try {
                out.write(bytes);
                out.flush();
            } finally {
                out.close();
            }
            call.resolve(statDoc(tree, docId));
        } catch (Exception e) {
            call.reject("write failed: " + e.getMessage());
        }
    }

    @PluginMethod
    public void remove(PluginCall call) {
        try {
            Uri tree = Uri.parse(call.getString("tree"));
            Uri doc = DocumentsContract.buildDocumentUriUsingTree(tree, call.getString("doc"));
            boolean ok = DocumentsContract.deleteDocument(cr(), doc);
            if (!ok) {
                call.reject("Could not delete");
                return;
            }
            call.resolve();
        } catch (Exception e) {
            call.reject("delete failed: " + e.getMessage());
        }
    }
}
