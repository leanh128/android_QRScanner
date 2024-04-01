package com.vn.qrscanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.AdRequest
import com.vn.qrscanner.databinding.ActivityMainBinding
import com.vn.qrscanner.utils.PermissionUtil

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val requiredPermissions = arrayOf(Manifest.permission.CAMERA)
        setContentView(R.layout.activity_main)
        if (!PermissionUtil.verifyPermission(this, Manifest.permission.CAMERA)) {
            PermissionUtil.requestPermissions(this, requiredPermissions, REQUEST_CODE_CAMERA)
        } else {
            init()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_CAMERA) {
            for (i in permissions.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    finish()
                    return
                }
            }
            init()
        }
    }


    private fun init() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.imgGenerateQr.setOnClickListener {
            openQRGenerator()
        }
        openScanView()
    }

    override fun onBackPressed() {
        if (supportFragmentManager.findFragmentById(binding.fragmentContainerScanner.id) !is ScanFragment) {
            openScanView()
        } else {
            super.onBackPressed()
        }
    }

    fun openScanView() {
        if (supportFragmentManager.findFragmentById(binding.fragmentContainerScanner.id) is ScanFragment)
            return
        supportFragmentManager.findFragmentById(binding.fragmentContainerText.id)?.let {
            supportFragmentManager.beginTransaction().remove(it).commitNowAllowingStateLoss()
        }
        supportFragmentManager.beginTransaction()
            .replace(
                binding.fragmentContainerScanner.id,
                ScanFragment()
            )
            .commitNowAllowingStateLoss()
    }

    private fun openQRGenerator() {
        startActivity(Intent(this, QRGeneratorActivity::class.java))
    }

    fun setScreenTitle(title: String) {
        binding.tvScreenTitle.text = title
    }


    override fun onStop() {
        super.onStop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        private const val TAG = "MainActivity"
        const val REQUEST_CODE_CAMERA = 123
    }
}