package com.vn.qrscanner;

import android.app.SearchManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.zxing.qrcode.QRCodeWriter;
import com.vn.qrscanner.Utilities.StringUtil;
import com.vn.qrscanner.bases.BaseFragment;

public class ResultFragment extends BaseFragment implements View.OnClickListener {
    public static final String TAG = "ResultFragment";
    public static final String ARG_RESULT = "scan.result";
    public static final String ARG_CODE_TYPE = "code.type";
    public static final String ARG_COUNTRY = "country";

    private String mResult, mCodeType, mCountry;

    private TextView tvResult;
    private Button btnProcess;

    public static ResultFragment newInstance(String result, String codeType, String country) {

        Bundle args = new Bundle();
        args.putString(ARG_RESULT, result);
        args.putString(ARG_CODE_TYPE, codeType);
        args.putString(ARG_COUNTRY, country);
        ResultFragment fragment = new ResultFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mResult = getArguments().getString(ARG_RESULT);
            mCodeType = getArguments().getString(ARG_CODE_TYPE);
            mCountry = getArguments().getString(ARG_COUNTRY, "");
        }
    }

    @Override
    public int getLayoutResource() {
        return R.layout.fragment_result;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvResult = view.findViewById(R.id.tv_result);
        String displayText = mResult;
        if (!TextUtils.isEmpty(mCountry)) {
            displayText = mCountry + " - " + mResult;
        }
        tvResult.setText(displayText);
        btnProcess = view.findViewById(R.id.btn_process);
        view.findViewById(R.id.btn_cancel).setOnClickListener(this);
        btnProcess.setOnClickListener(this);

        // TODO: 6/1/2018 show add with ads id here 
        AdView mAdView = view.findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);

        if (StringUtil.isLink(mResult)) {
            btnProcess.setText("Go to link");
        } else btnProcess.setText("Search it");

    }

    //OnClickListener_
    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_process) {
            if (btnProcess.getText().equals("Go to link")) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(tvResult.getText().toString())));
            } else {
                Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
                intent.putExtra(SearchManager.QUERY, tvResult.getText().toString());
                startActivity(intent);
            }
        } else if (v.getId() == R.id.btn_cancel) {
            navigationManager.goBack();
        }
    }
    //_OnClickListener
}

