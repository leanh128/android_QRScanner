package anhlt.com.qrscanner;

import android.Manifest;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.WindowManager;

import anhlt.com.qrscanner.Utilities.PermissionUtil;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    public NavigationManager navigationManager;

    public static final int REQUEST_CODE_CAMERA = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        navigationManager = new NavigationManager(this, R.id.fragment_container);
        setContentView(R.layout.activity_main);
        String requiredPermissions[] = new String[]{Manifest.permission.CAMERA};
        PermissionUtil.requestPermissions(this, requiredPermissions, REQUEST_CODE_CAMERA);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        navigationManager.addPage(new ScanViewFragment());
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (navigationManager.getActiveFragment() instanceof ScanViewFragment) {
            finish();
        }
    }
}
