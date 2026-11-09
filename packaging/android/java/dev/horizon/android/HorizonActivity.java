package dev.horizon.android;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.system.ErrnoException;
import android.system.Os;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** SAF result callback and native mod loader. The native activity, UI and file operations stay Rust. */
public final class HorizonActivity extends android.app.NativeActivity {
    private static final int DOCUMENT_REQUEST = 4101;
    private static final int MOD_PERMISSIONS = 3;
    private static final String GAME_LIBRARY = "HORIZON_GAME_LIBRARY";

    private File modBuild;
    private boolean modPermissionsAsked;

    private static final String[][] MOD_PERMISSION_NAMES = {
        {"microphone", android.Manifest.permission.RECORD_AUDIO, "the microphone"},
    };

    private void loadGame() {
        File build = NativeMods.chosen(this, versionName());
        if (build != null) {
            try {
                Os.setenv(GAME_LIBRARY, build.getPath(), true);
                Os.setenv("STARLING_GAME_LIBRARY", build.getPath(), true);
                System.load(build.getPath());
                modBuild = build;
                return;
            } catch (UnsatisfiedLinkError | ErrnoException e) {
                try {
                    Os.unsetenv(GAME_LIBRARY);
                    Os.unsetenv("STARLING_GAME_LIBRARY");
                } catch (ErrnoException ignored) {
                }
                NativeMods.failed(this, e.getMessage());
            }
        }
        System.loadLibrary("horizon_android");
    }

    public String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        loadGame();
        super.onCreate(savedInstanceState);
    }

    /** The game drew its first frame: the build started. */
    public void firstFrame() {
        NativeMods.started(this);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                askModPermissions();
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && modBuild != null && !modPermissionsAsked) {
            askModPermissions();
        }
    }

    private void askModPermissions() {
        if (modPermissionsAsked || modBuild == null || NativeMods.permissionsDeclined(modBuild)) {
            return;
        }
        modPermissionsAsked = true;
        Map<String, List<String>> wanted = NativeMods.permissions(modBuild);
        final List<String> missing = new ArrayList<>();
        StringBuilder said = new StringBuilder();
        for (String[] known : MOD_PERMISSION_NAMES) {
            List<String> mods = wanted.get(known[0]);
            if (mods == null || checkSelfPermission(known[1]) == PackageManager.PERMISSION_GRANTED) {
                continue;
            }
            missing.add(known[1]);
            if (said.length() > 0) {
                said.append('\n');
            }
            said.append(modNames(mods)).append(mods.size() == 1 ? " wants " : " want ")
                    .append(known[2]).append('.');
        }
        if (missing.isEmpty()) {
            return;
        }
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("A native mod needs a permission")
                .setMessage(said + "\n\nAndroid asks you next. You can change it later in the "
                        + "app's settings.")
                .setPositiveButton("Continue", (dialog, which) -> {
                    requestPermissions(missing.toArray(new String[0]), MOD_PERMISSIONS);
                })
                .setNegativeButton("Not now", (dialog, which) -> {
                    NativeMods.declinePermissions(modBuild);
                })
                .show();
    }

    private static String modNames(List<String> ids) {
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                names.append(i == ids.size() - 1 ? " and " : ", ");
            }
            String id = ids.get(i);
            names.append(Character.toUpperCase(id.charAt(0))).append(id.substring(1));
        }
        return names.toString();
    }

    private native void nativeDocumentResult(int request, int result, String uri);

    @Override
    protected void onActivityResult(int request, int result, android.content.Intent data) {
        super.onActivityResult(request, result, data);
        if (request != DOCUMENT_REQUEST) return;
        nativeDocumentResult(request, result,
                data == null || data.getData() == null ? null : data.getData().toString());
    }
}
