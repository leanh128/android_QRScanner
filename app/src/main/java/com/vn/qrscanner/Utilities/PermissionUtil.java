package com.vn.qrscanner.Utilities;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class PermissionUtil {


    public static boolean verifyPermission(Context context, String permission) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }


    public static void requestPermissions(AppCompatActivity activity, String[] permissions, int requestCode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;

        ArrayList<String> missingPermissions = new ArrayList<>();
        for (String permission : permissions) {
            if (!verifyPermission(activity, permission)) {
                missingPermissions.add(permission);
            }
        }
        if (missingPermissions.size() != 0)
            activity.requestPermissions(missingPermissions.toArray(new String[missingPermissions.size()]), requestCode);
    }
}
