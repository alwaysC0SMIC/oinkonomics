package com.example.oinkonomics.ui.debttracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.oinkonomics.databinding.FragmentDebttrackerBinding

class DebtTrackerFragment : Fragment() {

    private var _binding: FragmentDebttrackerBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val debtTrackerViewModel =
            ViewModelProvider(this).get(DebtTrackerViewModel::class.java)

        _binding = FragmentDebttrackerBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Custom layout implemented - no need for placeholder text
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

