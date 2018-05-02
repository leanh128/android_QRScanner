package anhlt.com.qrscanner;

import android.support.annotation.IdRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;

public class NavigationManager {
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
    }

    public Fragment getActiveFragment() {
        return fragmentManager.findFragmentById(container);
    }

    public void goBack(){
        if(fragmentManager.getBackStackEntryCount() == 1){
            activity.finish();
        }
        fragmentManager.popBackStackImmediate();
    }
}
