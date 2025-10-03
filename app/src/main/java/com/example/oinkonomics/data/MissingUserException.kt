package com.example.oinkonomics.data

class MissingUserException : IllegalStateException(
    "Your account information could not be found. Please sign in again."
)
