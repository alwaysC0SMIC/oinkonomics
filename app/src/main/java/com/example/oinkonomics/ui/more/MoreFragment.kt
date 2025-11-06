package com.example.oinkonomics.ui.more

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.oinkonomics.auth.AuthActivity
import com.example.oinkonomics.data.SessionManager
import com.example.oinkonomics.databinding.FragmentMoreBinding
import java.io.InputStream

// SHOWS THE MORE TAB CONTENT.
class MoreFragment : Fragment() {

    private var _binding: FragmentMoreBinding? = null

    // THIS PROPERTY IS ONLY VALID BETWEEN ONCREATEVIEW AND ONDESTROYVIEW.
    private val binding get() = _binding!!

    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                requireContext().contentResolver.takePersistableUriPermission(uri, flags)
                loadImageFromUri(uri)
            } catch (e: SecurityException) {
                // IGNORE IF PERMISSION CANNOT BE PERSISTED
            }
        }
    }

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

        // SET UP EDIT PROFILE PICTURE BUTTON CLICK LISTENER
        binding.editProfilePictureButton.setOnClickListener {
            imagePicker.launch(arrayOf("image/*"))
        }

        // LOG OUT: CLEAR SESSION AND RETURN TO AUTH SCREEN.
        binding.logoutButton.setOnClickListener {
            SessionManager(requireContext()).clearSession()
            startActivity(Intent(requireContext(), AuthActivity::class.java))
            requireActivity().finish()
        }

        // CUSTOM LAYOUT IMPLEMENTED - NO NEED FOR PLACEHOLDER TEXT.
        return root
    }

    private fun loadImageFromUri(uri: Uri) {
        // LOADS AND DISPLAYS THE SELECTED IMAGE IN THE PROFILE PICTURE VIEW.
        try {
            val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
            val bitmap: Bitmap? = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            if (bitmap != null) {
                binding.profilePicture.setImageBitmap(bitmap)
                // MAKE THE IMAGEVIEW CIRCULAR
                binding.profilePicture.clipToOutline = true
                binding.profilePicture.outlineProvider = ViewOutlineProvider.BACKGROUND
            }
        } catch (e: Exception) {
            // HANDLE ERROR SILENTLY OR SHOW A TOAST
        }
    }

    override fun onDestroyView() {
        // CLEARS BINDING REFERENCES TO PREVENT LEAKS.
        super.onDestroyView()
        _binding = null
    }
}

