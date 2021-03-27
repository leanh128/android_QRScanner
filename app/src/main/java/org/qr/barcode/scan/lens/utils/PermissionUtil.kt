package org.qr.barcode.scan.lens.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import java.util.ArrayList

object PermissionUtil {
    fun verifyPermission(context: Context, permission: String?): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) true else context.checkSelfPermission(permission!!) == PackageManager.PERMISSION_GRANTED
    }

    fun requestPermissions(activity: AppCompatActivity, permissions: Array<String>, requestCode: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val missingPermissions = ArrayList<String>()
        for (permission in permissions) {
            if (!verifyPermission(activity, permission)) {
                missingPermissions.add(permission)
            }
        }
        if (missingPermissions.size != 0) activity.requestPermissions(missingPermissions.toTypedArray(), requestCode)
    }
}