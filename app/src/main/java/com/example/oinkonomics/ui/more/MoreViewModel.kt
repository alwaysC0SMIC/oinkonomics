package com.example.oinkonomics.ui.more

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

// PROVIDES PLACEHOLDER TEXT FOR THE MORE TAB.
class MoreViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        // HOLDS STATIC SAMPLE TEXT.
        value = "This is more Fragment"
    }
    val text: LiveData<String> = _text
}

