package com.example.oinkonomics.ui.notifications

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

// PROVIDES PLACEHOLDER TEXT FOR THE NOTIFICATIONS TAB.
class NotificationsViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        // HOLDS STATIC SAMPLE TEXT.
        value = "This is notifications Fragment"
    }
    val text: LiveData<String> = _text
}