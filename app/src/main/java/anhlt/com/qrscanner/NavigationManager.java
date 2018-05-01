package anhlt.com.qrscanner;

import android.support.annotation.IdRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

public class NavigationManager {
    public FragmentManager fragmentManager;
    public int container;

    public NavigationManager(FragmentManager fragmentManager, @IdRes int container) {
        this.fragmentManager = fragmentManager;
        this.container = container;
    }

    public void addPage(Fragment fragment) {
        fragmentManager.beginTransaction()
                .addToBackStack(fragment.toString())
                .replace(container, fragment)
                .commit();
    }
}
