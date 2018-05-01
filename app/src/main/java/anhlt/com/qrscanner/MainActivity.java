package anhlt.com.qrscanner;

import android.content.pm.PackageManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;

import anhlt.com.qrscanner.Utilities.PermissionUtil;
import me.dm7.barcodescanner.zxing.ZXingScannerView;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    public NavigationManager navigationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        navigationManager = new NavigationManager(getSupportFragmentManager(), R.id.fragment_container);
        setContentView(R.layout.activity_main);
        String requiredPermissions[] = new String[]{PackageManager.FEATURE_CAMERA};
        PermissionUtil.requestPermissions(this, requiredPermissions, 0);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        navigationManager.addPage(new ScanViewFragment());
    }
}
