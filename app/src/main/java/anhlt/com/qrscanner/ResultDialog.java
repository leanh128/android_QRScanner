package anhlt.com.qrscanner;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebView;
import android.widget.EditText;

import java.util.Objects;

public class ResultDialog extends DialogFragment {
    public static final String TAG = "ResultDialog";
    public static final String ARG_RESULT = "scan.result";
    public static final String ARG_CODE_TYPE = "code.type";

    public String codeType;
    public String resultText;

    public EditText edtResultText;

    public static ResultDialog newInstance(String result, String codeType) {

        Bundle args = new Bundle();
        args.putString(ARG_RESULT, result);
        args.putString(ARG_CODE_TYPE, codeType);
        ResultDialog fragment = new ResultDialog();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            codeType = getArguments().getString(ARG_CODE_TYPE);
            resultText = getArguments().getString(ARG_RESULT);
        }
        setCancelable(false);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_result_dialog, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        edtResultText = view.findViewById(R.id.edt_result);
        edtResultText.setText(codeType + " " + resultText);
    }

    @Override
    public int show(FragmentTransaction transaction, String tag) {
        getDialog().requestWindowFeature(Window.FEATURE_NO_TITLE);
        Objects.requireNonNull(getDialog().getWindow()).setBackgroundDrawableResource(android.R.color.transparent);
        return super.show(transaction, tag);
    }
}
