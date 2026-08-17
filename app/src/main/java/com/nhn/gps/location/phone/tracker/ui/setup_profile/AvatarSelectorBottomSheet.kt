package com.nhn.gps.location.phone.tracker.ui.setup_profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nhn.gps.location.phone.tracker.databinding.BottomSheetAvatarSelectorBinding
import com.nhn.gps.location.phone.tracker.util.AvatarHelper

class AvatarSelectorBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAvatarSelectorBinding? = null
    private val binding get() = _binding!!

    private var selectedKey: String = AvatarHelper.DEFAULT_AVATAR_KEY

    private val adapter by lazy {
        AvatarAdapter(selectedKey) { key ->
            selectedKey = key
            (binding.rvAvatars.adapter as? AvatarAdapter)?.setSelectedKey(key)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedKey = arguments?.getString(ARG_SELECTED_KEY) ?: AvatarHelper.DEFAULT_AVATAR_KEY
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAvatarSelectorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.rvAvatars.adapter = adapter
        adapter.submitList(AvatarHelper.avatars)
        adapter.setSelectedKey(selectedKey)

        binding.btnConfirm.setOnClickListener {
            setFragmentResult(REQUEST_KEY, bundleOf(RESULT_AVATAR_KEY to selectedKey))
            dismiss()
        }
    }

    override fun onDestroyView() {
        binding.rvAvatars.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "AvatarSelectorBottomSheet"
        const val REQUEST_KEY = "REQUEST_KEY_AVATAR"
        const val RESULT_AVATAR_KEY = "RESULT_AVATAR_KEY"
        private const val ARG_SELECTED_KEY = "ARG_SELECTED_KEY"

        fun newInstance(selectedKey: String? = null) = AvatarSelectorBottomSheet().apply {
            arguments = bundleOf(ARG_SELECTED_KEY to selectedKey)
        }
    }
}
