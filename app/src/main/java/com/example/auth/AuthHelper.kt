package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val AUTH_TAG = "Auth"

internal tailrec fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

fun attemptAutoSignIn(
  context: Context,
  credentialManager: CredentialManager,
  onAuthSuccess: () -> Unit,
  onUnauthenticated: () -> Unit,
  scope: CoroutineScope,
) {
  if (Firebase.auth.currentUser != null) {
    onAuthSuccess()
    return
  }
  val clientId =
    try {
      context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
      onUnauthenticated()
      return
    }

  val googleIdOption =
    GetGoogleIdOption.Builder()
      .setFilterByAuthorizedAccounts(true)
      .setServerClientId(clientId)
      .setAutoSelectEnabled(true)
      .build()

  val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

  scope.launch {
    try {
      val activityContext = context.findActivity() ?: context
      val result = credentialManager.getCredential(activityContext, request)
      val credential = result.credential
      if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        Firebase.auth.signInWithCredential(authCredential).await()
        onAuthSuccess()
      } else {
        onUnauthenticated()
      }
    } catch (e: Exception) {
      Log.d(AUTH_TAG, "Silent auto sign-in skipped: ${e.message}")
      onUnauthenticated()
    }
  }
}

fun onGoogleSignInClicked(
  context: Context,
  credentialManager: CredentialManager,
  onAuthSuccess: () -> Unit,
  onAuthError: (String) -> Unit,
  scope: CoroutineScope,
  onAuthCancelled: () -> Unit = {},
) {
  val clientId =
    try {
      context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
      onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
      return
    }

  val activity = context.findActivity()
  if (activity == null) {
    onAuthError("Unable to launch sign-in from this context")
    return
  }

  val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
  val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

  scope.launch {
    try {
      val result = credentialManager.getCredential(activity, request)
      val credential = result.credential
      if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        Firebase.auth.signInWithCredential(authCredential).await()
        onAuthSuccess()
      } else {
        onAuthError("Unexpected credential type received")
      }
    } catch (e: GetCredentialCancellationException) {
      Log.w(AUTH_TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
      onAuthCancelled()
    } catch (e: Exception) {
      Log.e(AUTH_TAG, "Google Sign-In failed", e)
      onAuthError(e.localizedMessage ?: "Sign in failed. Please try again.")
    }
  }
}

fun startPhoneNumberVerification(
  context: Context,
  phoneNumberE164: String,
  resendToken: PhoneAuthProvider.ForceResendingToken? = null,
  onCodeSent: (verificationId: String, token: PhoneAuthProvider.ForceResendingToken) -> Unit,
  onAutoVerified: (PhoneAuthCredential) -> Unit,
  onError: (String) -> Unit,
  auth: FirebaseAuth = Firebase.auth,
) {
  val activity = context.findActivity()
  if (activity == null) {
    onError("Unable to start OTP verification from this screen")
    return
  }

  val callbacks =
    object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
      override fun onVerificationCompleted(credential: PhoneAuthCredential) {
        onAutoVerified(credential)
      }

      override fun onVerificationFailed(e: FirebaseException) {
        Log.e(AUTH_TAG, "Phone OTP verification failed", e)
        val message =
          when (e) {
            is FirebaseAuthInvalidCredentialsException ->
              "Invalid mobile number. Include country code (e.g. +919876543210)."
            is FirebaseTooManyRequestsException ->
              "Too many OTP attempts from this device. Please wait a bit and retry."
            else -> e.localizedMessage ?: "Failed to send OTP. Ensure Phone Auth is enabled."
          }
        onError(message)
      }

      override fun onCodeSent(
        verificationId: String,
        token: PhoneAuthProvider.ForceResendingToken,
      ) {
        onCodeSent(verificationId, token)
      }
    }

  val optionsBuilder =
    PhoneAuthOptions.newBuilder(auth)
      .setPhoneNumber(phoneNumberE164)
      .setTimeout(60L, TimeUnit.SECONDS)
      .setActivity(activity)
      .setCallbacks(callbacks)
  if (resendToken != null) {
    optionsBuilder.setForceResendingToken(resendToken)
  }
  PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build())
}

fun verifyPhoneOtpCode(
  verificationId: String,
  otpCode: String,
  onSuccess: () -> Unit,
  onError: (String) -> Unit,
  scope: CoroutineScope,
  auth: FirebaseAuth = Firebase.auth,
) {
  val cleanCode = otpCode.trim()
  if (cleanCode.length < 6) {
    onError("Enter the 6-digit OTP sent to your mobile number")
    return
  }
  val credential = PhoneAuthProvider.getCredential(verificationId, cleanCode)
  signInWithPhoneCredential(credential, onSuccess, onError, scope, auth)
}

fun signInWithPhoneCredential(
  credential: PhoneAuthCredential,
  onSuccess: () -> Unit,
  onError: (String) -> Unit,
  scope: CoroutineScope,
  auth: FirebaseAuth = Firebase.auth,
) {
  scope.launch {
    try {
      auth.signInWithCredential(credential).await()
      onSuccess()
    } catch (e: FirebaseAuthInvalidCredentialsException) {
      onError("Incorrect OTP code. Please check and try again.")
    } catch (e: Exception) {
      Log.e(AUTH_TAG, "Phone credential sign-in failed", e)
      onError(e.localizedMessage ?: "OTP verification failed")
    }
  }
}

fun signOutUser(
  credentialManager: CredentialManager,
  scope: CoroutineScope,
  onSignOutComplete: () -> Unit = {},
) {
  scope.launch {
    withContext(NonCancellable) {
      try {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
      } catch (e: Exception) {
        Log.w(AUTH_TAG, "Failed to clear credential state: ${e.message}")
      } finally {
        Firebase.auth.signOut()
        onSignOutComplete()
      }
    }
  }
}
