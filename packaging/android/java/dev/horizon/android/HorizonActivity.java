package dev.horizon.android;

/** SAF result callback only. The native activity, UI and file operations stay Rust. */
public final class HorizonActivity extends android.app.NativeActivity {
    static { System.loadLibrary("horizon_android"); }
    private native void nativeDocumentResult(int request, int result, String uri);

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dev.horizon.android.diagnostics.ProcessTracker.INSTANCE.setGameRunning(true);
    }

    @Override
    public void onBackPressed() {
        showQuitConfirmationDialog();
    }

    private void showQuitConfirmationDialog() {
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.quit_dialog_title)
            .setMessage(R.string.quit_dialog_message)
            .setPositiveButton(R.string.confirm, (dialog, which) -> {
                dialog.dismiss();
                dev.horizon.android.ui.components.TeardownLifecycleManager.INSTANCE.executeTeardown(this);
            })
            .setNegativeButton(R.string.cancel, (dialog, which) -> {
                dialog.dismiss();
            })
            .setCancelable(true)
            .show();
    }

    @Override
    protected void onDestroy() {
        dev.horizon.android.diagnostics.ProcessTracker.INSTANCE.setGameRunning(false);
        dev.horizon.android.service.BlazeKeepAliveService.Companion.stop(this);
        super.onDestroy();
    }

    @Override protected void onActivityResult(int request, int result, android.content.Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 4101) return;
        nativeDocumentResult(request, result,
                data == null || data.getData() == null ? null : data.getData().toString());
    }
}
