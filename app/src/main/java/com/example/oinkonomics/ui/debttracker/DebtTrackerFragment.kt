package com.example.oinkonomics.ui.debttracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.oinkonomics.databinding.FragmentDebttrackerBinding

// SHOWS THE DEBT TRACKER SCREEN CONTENT.
class DebtTrackerFragment : Fragment() {

    private var _binding: FragmentDebttrackerBinding? = null

    // THIS PROPERTY IS ONLY VALID BETWEEN ONCREATEVIEW AND ONDESTROYVIEW.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // INFLATES THE LAYOUT AND PREPARES VIEW BINDING.
        val debtTrackerViewModel =
            ViewModelProvider(this).get(DebtTrackerViewModel::class.java)

        _binding = FragmentDebttrackerBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // CUSTOM LAYOUT IMPLEMENTED - NO NEED FOR PLACEHOLDER TEXT.
        return root
    }

    override fun onDestroyView() {
        // CLEARS THE BINDING REFERENCE TO AVOID LEAKS.
        super.onDestroyView()
        _binding = null
    }
}

