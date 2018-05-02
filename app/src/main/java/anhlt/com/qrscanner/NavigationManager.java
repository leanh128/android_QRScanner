package anhlt.com.qrscanner;

import android.support.annotation.IdRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

public class NavigationManager {
    public static final String TAG = "NavigationManager";
    public FragmentManager fragmentManager;
    public int container;
    public AppCompatActivity activity;

    public NavigationManager(AppCompatActivity activity, @IdRes int container) {
        this.fragmentManager = activity.getSupportFragmentManager();
        this.container = container;
        this.activity = activity;
    }

    public void addPage(Fragment fragment) {
        fragmentManager.beginTransaction()
                .addToBackStack(fragment.toString())
                .replace(container, fragment)
                .commit();
        Log.d(TAG, "addPage: " + fragment.toString());
    }

    public Fragment getActiveFragment() {
        Fragment fragment = fragmentManager.findFragmentById(container);
        Log.d(TAG, "getActiveFragment: " + fragment.toString());
        return fragment;
    }

    public void goBack() {
        if (fragmentManager.getBackStackEntryCount() <= 1) {
            activity.finish();
        }
        fragmentManager.popBackStackImmediate();
    }
}
