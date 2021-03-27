package com.vn.qrscanner;

import android.os.Handler;

import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import android.util.Log;
import android.widget.Toast;


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
            Toast.makeText(activity, "Bye", Toast.LENGTH_SHORT).show();
            new Handler().postDelayed(new Runnable() {

                public void run() {
                    activity.finish();
                }
            },500);

        } else {
            fragmentManager.popBackStackImmediate();
        }
    }
}
