package anhlt.com.qrscanner;

import android.app.SearchManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import anhlt.com.qrscanner.Utilities.StringUtil;
import anhlt.com.qrscanner.bases.BaseFragment;

public class ResultFragment extends BaseFragment implements View.OnClickListener {
    public static final String TAG = "ResultFragment";
    public static final String ARG_RESULT = "scan.result";
    public static final String ARG_CODE_TYPE = "code.type";

    private String mResult, mCodeType;

    private TextView tvResult;
    private Button btnProcess;

    public static ResultFragment newInstance(String result, String codeType) {

        Bundle args = new Bundle();
        args.putString(ARG_RESULT, result);
        args.putString(ARG_CODE_TYPE, codeType);
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
        tvResult.setText(mResult);
        btnProcess = view.findViewById(R.id.btn_process);
        view.findViewById(R.id.btn_cancel).setOnClickListener(this);
        btnProcess.setOnClickListener(this);
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
                intent.putExtra(SearchManager.QUERY,tvResult.getText().toString());
                startActivity(intent);
            }
        } else if (v.getId() == R.id.btn_cancel) {
            navigationManager.goBack();
        }
    }
    //_OnClickListener
}

