package com.vn.qrscanner;

import android.Manifest;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.WindowManager;

import com.google.android.gms.ads.MobileAds;
import com.vn.qrscanner.Utilities.PermissionUtil;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    public NavigationManager navigationManager;

    public static final int REQUEST_CODE_CAMERA = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeAds();
        navigationManager = new NavigationManager(this, R.id.fragment_container);
        String requiredPermissions[] = new String[]{Manifest.permission.CAMERA};
        setContentView(R.layout.activity_main);
        if (!PermissionUtil.verifyPermission(this, Manifest.permission.CAMERA)) {
            PermissionUtil.requestPermissions(this, requiredPermissions, REQUEST_CODE_CAMERA);
        } else {
            openScanView();
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_CAMERA) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    finish();
                    return;
                }
            }
            openScanView();
        }
    }

    public void initializeAds(){
        MobileAds.initialize(this);
    }
    @Override
    public void onBackPressed() {
        navigationManager.goBack();
    }

    public void openScanView() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        navigationManager.addPage(new ScanViewFragment());
    }
}
