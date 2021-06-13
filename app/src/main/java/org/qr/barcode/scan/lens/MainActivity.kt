package org.qr.barcode.scan.lens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import org.qr.barcode.scan.lens.databinding.ActivityMainBinding
import org.qr.barcode.scan.lens.utils.PermissionUtil

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val requiredPermissions = arrayOf(Manifest.permission.CAMERA)
        setContentView(R.layout.activity_main)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            window.navigationBarColor = ContextCompat.getColor(this, R.color.colorPrimaryDark)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                window.navigationBarDividerColor = ContextCompat.getColor(this, android.R.color.white)
            }
        }
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
        MobileAds.initialize(this)
        binding.adView.loadAd(AdRequest.Builder().build())
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

    fun showResult(result: String, country: String = "") {
        if (supportFragmentManager.findFragmentById(binding.fragmentContainerText.id) is ResultFragment)
            return
        supportFragmentManager.findFragmentById(binding.fragmentContainerScanner.id)?.let {
            supportFragmentManager.beginTransaction().remove(it).commitNowAllowingStateLoss()
        }
        supportFragmentManager.beginTransaction()
                .replace(
                        binding.fragmentContainerText.id,
                        ResultFragment.newInstance(result, country))
                .commitNowAllowingStateLoss()
    }

    private fun openQRGenerator() {
        if (supportFragmentManager.findFragmentById(binding.fragmentContainerText.id) is QRGeneratorFragment)
            return
        supportFragmentManager.findFragmentById(binding.fragmentContainerScanner.id)?.let {
            supportFragmentManager.beginTransaction().remove(it).commitNowAllowingStateLoss()
        }
        supportFragmentManager.beginTransaction()
                .replace(
                        binding.fragmentContainerText.id,
                        QRGeneratorFragment())
                .commitNowAllowingStateLoss()
    }

    fun setScreenTitle(title: String) {
        binding.tvScreenTitle.text = title
    }


    override fun onStop() {
        super.onStop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun scrollToBottom() {
        binding.scrollView.smoothScrollTo(0, binding.scrollView.bottom)
    }

    fun scrollToTop() {
        binding.scrollView.smoothScrollTo(0, binding.scrollView.top)
    }

    companion object {
        private const val TAG = "MainActivity"
        const val REQUEST_CODE_CAMERA = 123
    }
}