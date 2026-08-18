package com.nhn.gps.location.phone.tracker.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.viewbinding.ViewBinding
import com.nhn.gps.location.phone.tracker.navigation.AppDestination
import com.nhn.gps.location.phone.tracker.navigation.NavigationManager
import com.nhn.gps.location.phone.tracker.ui.main.MainActivity
import javax.inject.Inject

abstract class BaseFragment<VB : ViewBinding, VM : ViewModel> : Fragment() {

    @Inject
    lateinit var navigationManager: NavigationManager

    private var _binding: VB? = null

    protected val binding: VB
        get() = checkNotNull(_binding) {
            "Binding is only available between onCreateView and onDestroyView"
        }

    protected abstract val viewModel: VM

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
        observeData()
    }

    protected abstract fun createBinding(inflater: LayoutInflater, container: ViewGroup?): VB

    protected abstract fun setupViews(savedInstanceState: Bundle?)

    protected open fun observeData() = Unit

    protected fun withBinding(block: VB.() -> Unit) {
        _binding?.block()
    }

    protected fun handleToolbarBack() {
        (activity as? MainActivity)?.navigateBackWithAd() ?: requireActivity().finish()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
