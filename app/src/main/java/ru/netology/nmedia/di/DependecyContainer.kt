package ru.netology.nmedia.di


import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.messaging.FirebaseMessaging


class DependencyContainer {

    val firebaseMessaging: FirebaseMessaging by lazy { FirebaseMessaging.getInstance() }

    val googleApiAvailability: GoogleApiAvailability by lazy { GoogleApiAvailability.getInstance() }
}


