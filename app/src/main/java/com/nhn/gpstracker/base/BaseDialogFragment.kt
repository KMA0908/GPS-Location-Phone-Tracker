package com.nhn.gpstracker.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.viewbinding.ViewBinding

abstract class BaseDialogFragment<VB : ViewBinding> : DialogFragment() {

    private var _binding: VB? = null

    protected val binding: VB
        get() = checkNotNull(_binding) {
            "Binding is only available between onCreateView and onDestroyView"
        }

    final override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = createBinding(inflater, container)
        return binding.root
    }

    final override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(savedInstanceState)
    }

    protected abstract fun createBinding(inflater: LayoutInflater, container: ViewGroup?): VB

    protected abstract fun setupViews(savedInstanceState: Bundle?)

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
