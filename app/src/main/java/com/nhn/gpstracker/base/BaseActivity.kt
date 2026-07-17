package com.nhn.gpstracker.base

import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModel
import androidx.viewbinding.ViewBinding

abstract class BaseActivity<VB : ViewBinding, VM : ViewModel> : AppCompatActivity() {

    private var _binding: VB? = null

    protected val binding: VB
        get() = checkNotNull(_binding) {
            "Binding is only available between onCreate and onDestroy"
        }

    protected abstract val viewModel: VM

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        _binding = createBinding(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()
        setupViews(savedInstanceState)
        observeData()
    }

    protected abstract fun createBinding(inflater: LayoutInflater): VB

    protected abstract fun setupViews(savedInstanceState: Bundle?)

    protected open fun observeData() = Unit

    private fun applySystemBarInsets() {
        val initialLeft = binding.root.paddingLeft
        val initialTop = binding.root.paddingTop
        val initialRight = binding.root.paddingRight
        val initialBottom = binding.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                initialLeft + bars.left,
                initialTop + bars.top,
                initialRight + bars.right,
                initialBottom + bars.bottom,
            )
            insets
        }
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }
}
