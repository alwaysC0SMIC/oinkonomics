package com.example.oinkonomics.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

// PROVIDES PLACEHOLDER DATA FOR THE DASHBOARD TAB.
class DashboardViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        // HOLDS STATIC SAMPLE TEXT.
        value = "This is dashboard Fragment"
    }
    val text: LiveData<String> = _text
}