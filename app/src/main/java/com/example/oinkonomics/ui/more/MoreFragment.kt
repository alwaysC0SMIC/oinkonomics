package com.example.oinkonomics.ui.more

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.oinkonomics.databinding.FragmentMoreBinding

// SHOWS THE MORE TAB CONTENT.
class MoreFragment : Fragment() {

    private var _binding: FragmentMoreBinding? = null

    // THIS PROPERTY IS ONLY VALID BETWEEN ONCREATEVIEW AND ONDESTROYVIEW.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // INFLATES THE MORE TAB LAYOUT.
        val moreViewModel =
            ViewModelProvider(this).get(MoreViewModel::class.java)

        _binding = FragmentMoreBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // CUSTOM LAYOUT IMPLEMENTED - NO NEED FOR PLACEHOLDER TEXT.
        return root
    }

    override fun onDestroyView() {
        // CLEARS BINDING REFERENCES TO PREVENT LEAKS.
        super.onDestroyView()
        _binding = null
    }
}

