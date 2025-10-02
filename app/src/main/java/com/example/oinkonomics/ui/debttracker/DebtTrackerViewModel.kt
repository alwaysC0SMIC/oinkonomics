package com.example.oinkonomics.ui.debttracker

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class DebtTrackerViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is debt tracker Fragment"
    }
    val text: LiveData<String> = _text
}

