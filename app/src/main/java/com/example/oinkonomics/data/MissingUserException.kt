package com.example.oinkonomics.data

// SIGNALS THAT THE CURRENT USER RECORD IS UNAVAILABLE.
class MissingUserException : IllegalStateException(
    "Your account information could not be found. Please sign in again."
)
