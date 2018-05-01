package anhlt.com.qrscanner;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;

import com.google.zxing.Result;

import anhlt.com.qrscanner.bases.BaseFragment;
import me.dm7.barcodescanner.zxing.ZXingScannerView;

public class ScanViewFragment extends BaseFragment implements ZXingScannerView.ResultHandler {
    private static final String TAG = "ScannerView";
    private ZXingScannerView mScannerView;

    @Override
    public int getLayoutResource() {
        return R.layout.fragment_scan_view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mScannerView = view.findViewById(R.id.vScanner);
    }

    @Override
    public void onResume() {
        super.onResume();
        mScannerView.setResultHandler(this);
        mScannerView.startCamera();
    }

    @Override
    public void onPause() {
        super.onPause();
        mScannerView.stopCamera();
    }

    //ZXingScannerView.ResultHandler_
    @Override
    public void handleResult(Result result) {
        Log.v(TAG, result.getText());
        Log.v(TAG, result.getBarcodeFormat().toString());
        ResultFragment fragment = ResultFragment.newInstance(result.getText(), result.getBarcodeFormat().toString());
        navigationManager.addPage(fragment);
        mScannerView.stopCamera();
    }
    //_ZXingScannerView.ResultHandler
}
