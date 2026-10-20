package com.vn.qrscanner.features.scanner.result

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.vn.qrscanner.features.scanner.ScanResultItem
import com.vn.qrscanner.databinding.FragmentUrlResultBinding
import com.vn.qrscanner.utils.QrCodeUtils

/** Result screen for URL codes. Self-contained: own layout, own binding, own event handling. */
class UrlResultFragment : Fragment() {

    private var _binding: FragmentUrlResultBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScanResultViewModel by lazy {
        ViewModelProvider(this)[ScanResultViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUrlResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnAction.setOnClickListener { viewModel.onActionClick() }
        binding.layoutResult.setOnClickListener { viewModel.onActionClick() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.tvResult.text = state.result
                        binding.ivPreview.setImageBitmap(state.preview)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is ScanResultEvent.OpenUrl ->
                                QrCodeUtils.openUrl(requireContext(), event.url)
                            is ScanResultEvent.Search ->
                                QrCodeUtils.searchWeb(requireContext(), event.text)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(item: ScanResultItem) = UrlResultFragment().apply {
            arguments = bundleOf(ScanResultViewModel.ARG_RESULT to item)
        }
    }
}
