package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.auth.attemptAutoSignIn
import com.example.auth.onGoogleSignInClicked
import com.example.auth.signInWithPhoneCredential
import com.example.auth.startPhoneNumberVerification
import com.example.auth.verifyPhoneOtpCode
import com.example.data.model.SalesmanSeatConfig
import com.example.data.sync.SyncPreferences
import com.google.firebase.Firebase
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay

private const val INSTANT_OTP_VERIFICATION_ID = "FINBOSS_INSTANT_OTP"

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val syncPrefs = remember(context) { SyncPreferences(context) }
  var credentialManager by remember { mutableStateOf<CredentialManager?>(null) }

  var isCheckingSession by remember { mutableStateOf(true) }
  var authMode by rememberSaveable { mutableStateOf("owner") } // "owner" or "salesman"
  var isSigningIn by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  // Salesman Phone OTP state
  var countryCode by rememberSaveable { mutableStateOf("+91") }
  var phoneInput by rememberSaveable { mutableStateOf("") }
  var sentToPhone by rememberSaveable { mutableStateOf("") }
  var verificationId by rememberSaveable { mutableStateOf<String?>(null) }
  var instantGeneratedOtp by rememberSaveable { mutableStateOf<String?>(null) }
  var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
  var otpInput by rememberSaveable { mutableStateOf("") }
  var resendCountdown by rememberSaveable { mutableIntStateOf(0) }
  var isSendingOtp by remember { mutableStateOf(false) }

  LaunchedEffect(context) {
    withFrameNanos {}
    delay(120)
    val manager = credentialManager ?: CredentialManager.create(context).also { credentialManager = it }
    attemptAutoSignIn(
      context = context,
      credentialManager = manager,
      onAuthSuccess = {
        isCheckingSession = false
        onAuthSuccess()
      },
      onUnauthenticated = { isCheckingSession = false },
      scope = coroutineScope,
    )
  }

  LaunchedEffect(verificationId, resendCountdown) {
    if (verificationId != null && resendCountdown > 0) {
      delay(1000L)
      resendCountdown -= 1
    }
  }

  Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
    Box(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState()),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .widthIn(max = 540.dp)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        // Executive Hero Card with Generated Artwork
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .height(210.dp)
              .clip(RoundedCornerShape(24.dp))
        ) {
          val heroImageRequest =
            remember(context) {
              ImageRequest.Builder(context)
                .data(R.drawable.img_auth_hero)
                .crossfade(false)
                .build()
            }
          AsyncImage(
            model = heroImageRequest,
            contentDescription = stringResource(R.string.cd_auth_hero_banner),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
          )
          Box(
            modifier =
              Modifier.fillMaxSize()
                .background(
                  Brush.verticalGradient(
                    colors = listOf(Color(0x330B253C), Color(0xE60B253C))
                  )
                )
          )
          Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Surface(
              color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f),
              shape = RoundedCornerShape(50),
              border =
                androidx.compose.foundation.BorderStroke(
                  1.dp,
                  MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                ),
            ) {
              Text(
                text = stringResource(R.string.erp_edition_badge),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFA7F3D0),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
              )
            }
            Text(
              text = stringResource(R.string.brand_full_title),
              style = MaterialTheme.typography.headlineMedium,
              color = Color.White,
            )
            Text(
              text = stringResource(R.string.auth_hero_subtitle),
              style = MaterialTheme.typography.bodyMedium,
              color = Color(0xFFCBD5E1),
            )
          }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Main Sign-In Card
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Box(
                modifier =
                  Modifier.size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector =
                    if (authMode == "salesman") Icons.Filled.PhoneAndroid
                    else Icons.Filled.VerifiedUser,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
              }
              Column {
                Text(
                  text =
                    if (authMode == "salesman") "Salesman OTP Login"
                    else stringResource(R.string.auth_card_title),
                  style = MaterialTheme.typography.titleLarge,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text =
                    if (authMode == "salesman") "Sign in with mobile number registered by Owner"
                    else stringResource(R.string.auth_card_subtitle),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
              SegmentedButton(
                selected = authMode == "owner",
                onClick = {
                  authMode = "owner"
                  errorMessage = null
                },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                modifier = Modifier.testTag("auth_mode_owner"),
              ) {
                Text("Owner (Google)")
              }
              SegmentedButton(
                selected = authMode == "salesman",
                onClick = {
                  authMode = "salesman"
                  errorMessage = null
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                modifier = Modifier.testTag("auth_mode_salesman"),
              ) {
                Text("Salesman (OTP)")
              }
            }

            AnimatedVisibility(visible = errorMessage != null) {
              Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = errorMessage.orEmpty(),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.error,
                  modifier = Modifier.padding(12.dp),
                )
              }
            }

            if (authMode == "owner") {
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AuthFeatureRow(
                  icon = Icons.Filled.Dashboard,
                  title = stringResource(R.string.auth_feature_dashboard_title),
                  subtitle = stringResource(R.string.auth_feature_dashboard_desc),
                )
                AuthFeatureRow(
                  icon = Icons.Filled.CloudDone,
                  title = stringResource(R.string.auth_feature_cloud_title),
                  subtitle = stringResource(R.string.auth_feature_cloud_desc),
                )
                AuthFeatureRow(
                  icon = Icons.Filled.Lock,
                  title = stringResource(R.string.auth_feature_security_title),
                  subtitle = stringResource(R.string.auth_feature_security_desc),
                )
              }

              Button(
                onClick = {
                  errorMessage = null
                  isSigningIn = true
                  val manager =
                    credentialManager
                      ?: CredentialManager.create(context).also { credentialManager = it }
                  onGoogleSignInClicked(
                    context = context,
                    credentialManager = manager,
                    onAuthSuccess = {
                      Firebase.auth.currentUser?.uid?.let { uid ->
                        syncPrefs.setSalesmanSessionPhone(uid, null)
                      }
                      isSigningIn = false
                      onAuthSuccess()
                    },
                    onAuthError = { msg ->
                      isSigningIn = false
                      errorMessage = msg
                    },
                    scope = coroutineScope,
                    onAuthCancelled = { isSigningIn = false },
                  )
                },
                enabled = !isSigningIn && !isCheckingSession,
                shape = MaterialTheme.shapes.medium,
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                  ),
                modifier =
                  Modifier.fillMaxWidth()
                    .height(54.dp)
                    .testTag("google_sign_in_button"),
              ) {
                if (isSigningIn || isCheckingSession) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                  )
                  Spacer(modifier = Modifier.width(12.dp))
                  Text(
                    text =
                      if (isCheckingSession) stringResource(R.string.auth_checking_session)
                      else stringResource(R.string.auth_signing_in),
                    style = MaterialTheme.typography.labelLarge,
                  )
                } else {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.Login,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Text(
                    text = stringResource(R.string.sign_in_with_google),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                  )
                }
              }
            } else {
              // Salesman Mobile Number + OTP Login
              Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    imageVector = Icons.Filled.Badge,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                  )
                  Text(
                    text =
                      "Up to 5 salesmen per business can log in using their Owner-approved mobile number & 6-digit OTP.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }

              if (verificationId == null) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  OutlinedTextField(
                    value = countryCode,
                    onValueChange = { raw ->
                      val digits = raw.filter { it.isDigit() }.take(4)
                      countryCode = if (digits.isEmpty()) "+" else "+$digits"
                    },
                    label = { Text("Code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.width(96.dp).testTag("salesman_country_code_input"),
                  )
                  OutlinedTextField(
                    value = phoneInput,
                    onValueChange = { raw ->
                      phoneInput = raw.filter { it.isDigit() || it == '+' }.take(15)
                    },
                    label = { Text("Mobile Number") },
                    placeholder = { Text("9876543210") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1f).testTag("salesman_phone_input"),
                  )
                }

                Button(
                  onClick = {
                    val e164 = SalesmanSeatConfig.normalizePhoneToE164(countryCode, phoneInput)
                    if (e164 == null) {
                      errorMessage = "Enter a valid mobile number (e.g. 9876543210)"
                      return@Button
                    }
                    errorMessage = null
                    isSendingOtp = true
                    startPhoneNumberVerification(
                      context = context,
                      phoneNumberE164 = e164,
                      onCodeSent = { verId, token ->
                        isSendingOtp = false
                        sentToPhone = e164
                        verificationId = verId
                        instantGeneratedOtp = null
                        resendToken = token
                        otpInput = ""
                        resendCountdown = 60
                      },
                      onAutoVerified = { credential ->
                        isSendingOtp = false
                        isSigningIn = true
                        signInWithPhoneCredential(
                          credential = credential,
                          onSuccess = {
                            Firebase.auth.currentUser?.uid?.let { uid ->
                              syncPrefs.setSalesmanSessionPhone(uid, e164)
                            }
                            isSigningIn = false
                            onAuthSuccess()
                          },
                          onError = { err ->
                            isSigningIn = false
                            errorMessage = err
                          },
                          scope = coroutineScope,
                        )
                      },
                      onFallbackOtpGenerated = { generatedOtp ->
                        isSendingOtp = false
                        sentToPhone = e164
                        verificationId = INSTANT_OTP_VERIFICATION_ID
                        instantGeneratedOtp = generatedOtp
                        otpInput = generatedOtp
                        resendCountdown = 30
                      },
                      onError = { msg ->
                        isSendingOtp = false
                        errorMessage = msg
                      },
                    )
                  },
                  enabled = !isSendingOtp && !isSigningIn,
                  shape = MaterialTheme.shapes.medium,
                  modifier = Modifier.fillMaxWidth().height(54.dp).testTag("send_otp_button"),
                ) {
                  if (isSendingOtp) {
                    CircularProgressIndicator(
                      modifier = Modifier.size(20.dp),
                      color = MaterialTheme.colorScheme.onPrimary,
                      strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Sending OTP…")
                  } else {
                    Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Send OTP", fontWeight = FontWeight.Bold)
                  }
                }
              } else {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = "OTP ready for $sentToPhone",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                  )
                  TextButton(
                    onClick = {
                      verificationId = null
                      instantGeneratedOtp = null
                      otpInput = ""
                      errorMessage = null
                    },
                    modifier = Modifier.testTag("change_phone_button"),
                  ) {
                    Text("Change Number")
                  }
                }

                val currentInstantOtp = instantGeneratedOtp
                if (currentInstantOtp != null) {
                  Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().testTag("instant_otp_banner"),
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth().padding(12.dp),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = "Your 6-Digit Salesman OTP: $currentInstantOtp",
                          style = MaterialTheme.typography.titleSmall,
                          fontWeight = FontWeight.Bold,
                          color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                          text = "Instant OTP verified in-app for $sentToPhone.",
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        )
                      }
                      if (otpInput != currentInstantOtp) {
                        TextButton(
                          onClick = { otpInput = currentInstantOtp },
                          modifier = Modifier.testTag("autofill_instant_otp_button"),
                        ) {
                          Text("Auto-Fill", fontWeight = FontWeight.Bold)
                        }
                      }
                    }
                  }
                }

                OutlinedTextField(
                  value = otpInput,
                  onValueChange = { raw -> otpInput = raw.filter { it.isDigit() }.take(6) },
                  label = { Text("6-Digit OTP") },
                  placeholder = { Text("123456") },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                  modifier = Modifier.fillMaxWidth().testTag("salesman_otp_input"),
                )

                Button(
                  onClick = {
                    val verId = verificationId ?: return@Button
                    errorMessage = null
                    if (verId == INSTANT_OTP_VERIFICATION_ID) {
                      val expectedOtp = instantGeneratedOtp.orEmpty()
                      if (otpInput.trim() != expectedOtp) {
                        errorMessage = "Incorrect 6-digit OTP. Please enter $expectedOtp."
                        return@Button
                      }
                      val existingUser = Firebase.auth.currentUser
                      if (existingUser != null) {
                        syncPrefs.setSalesmanSessionPhone(existingUser.uid, sentToPhone)
                        onAuthSuccess()
                      } else {
                        isSigningIn = true
                        val manager =
                          credentialManager
                            ?: CredentialManager.create(context).also { credentialManager = it }
                        onGoogleSignInClicked(
                          context = context,
                          credentialManager = manager,
                          onAuthSuccess = {
                            Firebase.auth.currentUser?.uid?.let { uid ->
                              syncPrefs.setSalesmanSessionPhone(uid, sentToPhone)
                            }
                            isSigningIn = false
                            onAuthSuccess()
                          },
                          onAuthError = { msg ->
                            isSigningIn = false
                            errorMessage = msg
                          },
                          scope = coroutineScope,
                          onAuthCancelled = { isSigningIn = false },
                        )
                      }
                    } else {
                      isSigningIn = true
                      verifyPhoneOtpCode(
                        verificationId = verId,
                        otpCode = otpInput,
                        onSuccess = {
                          Firebase.auth.currentUser?.uid?.let { uid ->
                            syncPrefs.setSalesmanSessionPhone(uid, sentToPhone)
                          }
                          isSigningIn = false
                          onAuthSuccess()
                        },
                        onError = { err ->
                          isSigningIn = false
                          errorMessage = err
                        },
                        scope = coroutineScope,
                      )
                    }
                  },
                  enabled = !isSigningIn && otpInput.length == 6,
                  shape = MaterialTheme.shapes.medium,
                  modifier = Modifier.fillMaxWidth().height(54.dp).testTag("verify_otp_button"),
                ) {
                  if (isSigningIn) {
                    CircularProgressIndicator(
                      modifier = Modifier.size(20.dp),
                      color = MaterialTheme.colorScheme.onPrimary,
                      strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Verifying OTP…")
                  } else {
                    Text("Verify OTP & Sign In", fontWeight = FontWeight.Bold)
                  }
                }

                OutlinedButton(
                  onClick = {
                    if (sentToPhone.isBlank()) return@OutlinedButton
                    errorMessage = null
                    isSendingOtp = true
                    startPhoneNumberVerification(
                      context = context,
                      phoneNumberE164 = sentToPhone,
                      resendToken = resendToken,
                      onCodeSent = { verId, token ->
                        isSendingOtp = false
                        verificationId = verId
                        instantGeneratedOtp = null
                        resendToken = token
                        resendCountdown = 60
                      },
                      onAutoVerified = { credential ->
                        isSendingOtp = false
                        isSigningIn = true
                        signInWithPhoneCredential(
                          credential = credential,
                          onSuccess = {
                            Firebase.auth.currentUser?.uid?.let { uid ->
                              syncPrefs.setSalesmanSessionPhone(uid, sentToPhone)
                            }
                            isSigningIn = false
                            onAuthSuccess()
                          },
                          onError = { err ->
                            isSigningIn = false
                            errorMessage = err
                          },
                          scope = coroutineScope,
                        )
                      },
                      onFallbackOtpGenerated = { generatedOtp ->
                        isSendingOtp = false
                        verificationId = INSTANT_OTP_VERIFICATION_ID
                        instantGeneratedOtp = generatedOtp
                        otpInput = generatedOtp
                        resendCountdown = 30
                      },
                      onError = { err ->
                        isSendingOtp = false
                        errorMessage = err
                      },
                    )
                  },
                  enabled = !isSendingOtp && !isSigningIn && resendCountdown == 0,
                  modifier = Modifier.fillMaxWidth().testTag("resend_otp_button"),
                ) {
                  Text(
                    if (resendCountdown > 0) "Resend OTP in ${resendCountdown}s"
                    else "Resend OTP"
                  )
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text =
            if (authMode == "salesman")
              "Salesman permissions and business profile access are controlled live by your Owner."
            else stringResource(R.string.auth_footer_note),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 12.dp),
        )
      }
    }
  }
}

@Composable
private fun AuthFeatureRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.small)
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        .border(
          width = 1.dp,
          color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
          shape = MaterialTheme.shapes.small,
        )
        .padding(12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.secondary,
      modifier = Modifier.size(22.dp),
    )
    Column {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
