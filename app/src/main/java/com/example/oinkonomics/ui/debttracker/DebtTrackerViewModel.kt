package com.example.oinkonomics.ui.debttracker

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

// PROVIDES PLACEHOLDER TEXT FOR THE DEBT TRACKER TAB.
class DebtTrackerViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        // HOLDS STATIC SAMPLE TEXT.
        value = "This is debt tracker Fragment"
    }
    val text: LiveData<String> = _text
}

