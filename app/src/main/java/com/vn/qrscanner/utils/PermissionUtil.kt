package com.vn.qrscanner.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import java.util.ArrayList

object PermissionUtil {
    fun verifyPermission(context: Context, permission: String?): Boolean {
        return context.checkSelfPermission(permission!!) == PackageManager.PERMISSION_GRANTED
    }

    fun requestPermissions(activity: AppCompatActivity, permissions: Array<String>, requestCode: Int) {
        val missingPermissions = ArrayList<String>()
        for (permission in permissions) {
            if (!verifyPermission(activity, permission)) {
                missingPermissions.add(permission)
            }
        }
        if (missingPermissions.size != 0) activity.requestPermissions(missingPermissions.toTypedArray(), requestCode)
    }
}