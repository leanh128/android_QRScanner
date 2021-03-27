package com.vn.qrscanner.bases

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import com.vn.qrscanner.MainActivity
import com.vn.qrscanner.NavigationManager

abstract class BaseFragment : Fragment() {
    @JvmField
    protected var navigationManager: NavigationManager? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navigationManager = (activity as? MainActivity)?.navigationManager
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(layoutResource, container, false)
    }

    @get:LayoutRes
    abstract val layoutResource: Int
}