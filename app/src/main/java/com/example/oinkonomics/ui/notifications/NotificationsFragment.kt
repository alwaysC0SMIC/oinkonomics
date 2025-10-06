package com.example.oinkonomics.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.oinkonomics.databinding.FragmentNotificationsBinding

// SHOWS THE NOTIFICATIONS TAB CONTENT.
class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null

    // THIS PROPERTY IS ONLY VALID BETWEEN ONCREATEVIEW AND ONDESTROYVIEW.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // INFLATES THE NOTIFICATIONS LAYOUT.
        val notificationsViewModel =
            ViewModelProvider(this).get(NotificationsViewModel::class.java)

        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
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