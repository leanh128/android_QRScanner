package com.vn.qrscanner;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.Result;

import com.google.zxing.ResultMetadataType;
import com.vn.qrscanner.bases.BaseFragment;


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
        String country= "";
        if(result.getResultMetadata()!= null){
            country = (String) result.getResultMetadata().get(ResultMetadataType.POSSIBLE_COUNTRY);
        }
        ResultFragment fragment = ResultFragment.newInstance(result.getText(), result.getBarcodeFormat().toString(),country );
        navigationManager.addPage(fragment);
        mScannerView.stopCamera();
    }
    //_ZXingScannerView.ResultHandler
}
