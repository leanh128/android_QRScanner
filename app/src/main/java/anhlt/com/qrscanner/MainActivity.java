package anhlt.com.qrscanner;

import android.content.pm.PackageManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;

import anhlt.com.qrscanner.Utilities.PermissionUtil;
import me.dm7.barcodescanner.zxing.ZXingScannerView;

public class MainActivity extends AppCompatActivity implements ZXingScannerView.ResultHandler {
    private static final String TAG = "MainActivity";
    private ZXingScannerView mScannerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mScannerView = findViewById(R.id.vScanner);
        String requiredPermissions[] = new String[]{PackageManager.FEATURE_CAMERA};
        PermissionUtil.requestPermissions(this, requiredPermissions, 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mScannerView.setResultHandler(this);
        mScannerView.startCamera();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mScannerView.stopCamera();
    }

    //ZXingScannerView.ResultHandler_
    @Override
    public void handleResult(Result result) {
        Log.v(TAG, result.getText());
        Log.v(TAG, result.getBarcodeFormat().toString());
        ResultDialog dialog = ResultDialog.newInstance(result.getText(), result.getBarcodeFormat().toString());
        dialog.show(getSupportFragmentManager(), "");
        mScannerView.stopCamera();
    }
    //_ZXingScannerView.ResultHandler
}
