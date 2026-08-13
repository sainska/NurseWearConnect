package com.example.nursewearconnect.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nursewearconnect.utils.AppUtils
import com.example.nursewearconnect.ui.theme.*
import com.example.nursewearconnect.ui.components.PasswordStrengthSection
import com.example.nursewearconnect.ui.viewmodel.RecoveryViewModel
import com.example.nursewearconnect.ui.viewmodel.ViewModelFactory
import androidx.compose.ui.platform.LocalContext
import com.example.nursewearconnect.NurseWearApplication

enum class RecoveryState {
    METHOD_SELECTION, OTP_VERIFICATION, NEW_PASSWORD, SUCCESS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordRecoveryScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as? NurseWearApplication
    val viewModel: RecoveryViewModel = if (app != null) {
        viewModel(factory = ViewModelFactory(app))
    } else {
        viewModel()
    }
    var currentState by remember { mutableStateOf(RecoveryState.METHOD_SELECTION) }
    var selectedMethod by remember { mutableStateOf("email") }
    var isPasswordValid by remember { mutableStateOf(false) }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    val otpValues = remember { mutableStateListOf("", "", "", "") }
    
    val uiError by viewModel.error.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSuccess by viewModel.success.collectAsState()
    val isOtpVerified by viewModel.otpVerified.collectAsState()
    val resendTimer by viewModel.resendTimer.collectAsState()

    LaunchedEffect(isSuccess) {
        if (isSuccess) {
            if (currentState == RecoveryState.METHOD_SELECTION) {
                currentState = RecoveryState.OTP_VERIFICATION
            } else if (currentState == RecoveryState.NEW_PASSWORD) {
                currentState = RecoveryState.SUCCESS
            }
        }
    }

    LaunchedEffect(isOtpVerified) {
        if (isOtpVerified) {
            currentState = RecoveryState.NEW_PASSWORD
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
    ) {
        // Decorative Gradients
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .fillMaxHeight(0.4f)
                .align(Alignment.TopStart)
                .offset(x = (-80).dp, y = (-80).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Brand100.copy(alpha = 0.4f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header
            if (currentState != RecoveryState.SUCCESS) {
                CenterAlignedTopAppBar(
                    title = { 
                        Text("Reset Password", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium) 
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                viewModel.clearState()
                                if (currentState == RecoveryState.METHOD_SELECTION) {
                                    onBack()
                                } else {
                                    currentState = when (currentState) {
                                        RecoveryState.OTP_VERIFICATION -> {
                                            otpValues.indices.forEach { otpValues[it] = "" }
                                            RecoveryState.METHOD_SELECTION
                                        }
                                        RecoveryState.NEW_PASSWORD -> RecoveryState.OTP_VERIFICATION
                                        else -> RecoveryState.METHOD_SELECTION
                                    }
                                }
                            },
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(40.dp)
                                .background(Color.White, CircleShape)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Slate900,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // Error display
                uiError?.let {
                    Surface(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        color = Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFEE2E2))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                it,
                                color = Color(0xFF991B1B),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                AnimatedContent(
                    targetState = currentState,
                    transitionSpec = {
                        if (targetState.ordinal > initialState.ordinal) {
                            slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                        } else {
                            slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                        }
                    },
                    label = "recovery_state"
                ) { state ->
                    when (state) {
                        RecoveryState.METHOD_SELECTION -> MethodSelectionContent(
                            email = emailInput,
                            onEmailChange = { emailInput = it },
                            selectedMethod = selectedMethod,
                            onMethodSelected = { selectedMethod = it }
                        )
                        RecoveryState.OTP_VERIFICATION -> OtpVerificationContent(
                            selectedMethod = selectedMethod,
                            email = emailInput,
                            otpValues = otpValues,
                            resendTimer = resendTimer,
                            onResend = {
                                otpValues.indices.forEach { otpValues[it] = "" }
                                viewModel.requestPasswordReset(emailInput)
                            },
                            onVerify = { otp ->
                                if (!isLoading) {
                                    viewModel.verifyOtp(emailInput, otp)
                                }
                            }
                        )
                        RecoveryState.NEW_PASSWORD -> NewPasswordContent(
                            onPasswordValid = { isPasswordValid = it },
                            onPasswordChange = { passwordInput = it }
                        )
                        RecoveryState.SUCCESS -> SuccessContent()
                    }
                }
            }

            // Footer
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 16.dp,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .navigationBarsPadding()
                ) {
                    Button(
                        onClick = {
                            when (currentState) {
                                RecoveryState.METHOD_SELECTION -> {
                                    if (emailInput.isNotBlank()) {
                                        viewModel.requestPasswordReset(emailInput)
                                    }
                                }
                                RecoveryState.OTP_VERIFICATION -> {
                                    val otp = otpValues.joinToString("")
                                    if (otp.length == 4 && !isLoading) {
                                        viewModel.verifyOtp(emailInput, otp)
                                    }
                                }
                                RecoveryState.NEW_PASSWORD -> {
                                    if (isPasswordValid) {
                                        viewModel.updatePassword(emailInput, otpValues.joinToString(""), passwordInput)
                                    }
                                }
                                RecoveryState.SUCCESS -> {
                                    onSuccess()
                                }
                            }
                        },
                        enabled = !isLoading && when (currentState) {
                            RecoveryState.METHOD_SELECTION -> emailInput.isNotBlank() && AppUtils.isEmail(emailInput)
                            RecoveryState.OTP_VERIFICATION -> otpValues.joinToString("").length == 4
                            RecoveryState.NEW_PASSWORD -> isPasswordValid
                            RecoveryState.SUCCESS -> true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Brand600,
                            disabledContainerColor = Slate200
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = when (currentState) {
                                    RecoveryState.METHOD_SELECTION -> "Send Reset Code"
                                    RecoveryState.OTP_VERIFICATION -> "Verify Code"
                                    RecoveryState.NEW_PASSWORD -> "Complete Reset"
                                    RecoveryState.SUCCESS -> "Log In Now"
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (currentState != RecoveryState.SUCCESS) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Remembered your password?",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp,
                            color = Slate500
                        )
                        Text(
                            "Log In Instead",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onBack() }
                                .padding(top = 4.dp),
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Brand600
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MethodSelectionContent(
    email: String,
    onEmailChange: (String) -> Unit,
    selectedMethod: String,
    onMethodSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Surface(
            modifier = Modifier.size(80.dp),
            shape = RoundedCornerShape(24.dp),
            color = Brand50
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.LockReset,
                    contentDescription = null,
                    tint = Brand600,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            "Forgot Password?",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = Slate900
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "Don't worry! It happens. Please enter the email associated with your account.",
            textAlign = TextAlign.Center,
            fontSize = 15.sp,
            color = Slate500,
            lineHeight = 22.sp
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Registered Email") },
            placeholder = { Text("jane.doe@hospital.com") },
            leadingIcon = { Icon(Icons.Default.Email, null, tint = Slate400) },
            shape = RoundedCornerShape(16.dp),
            colors = AppUtils.standardOutlinedTextFieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            onClick = { onMethodSelected("email") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(
                width = 2.dp,
                color = if (selectedMethod == "email") Brand600 else Slate100
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(if (selectedMethod == "email") Brand50 else Slate50, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AlternateEmail,
                        contentDescription = null,
                        tint = if (selectedMethod == "email") Brand600 else Slate400,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Secure Reset Code", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate900)
                    Text(email.ifBlank { "Sent to your email" }, fontSize = 12.sp, color = Slate500)
                }
                RadioButton(
                    selected = selectedMethod == "email",
                    onClick = null,
                    colors = RadioButtonDefaults.colors(selectedColor = Brand600)
                )
            }
        }
    }
}

@Composable
fun OtpVerificationContent(
    selectedMethod: String,
    email: String,
    otpValues: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    resendTimer: Int,
    onResend: () -> Unit,
    onVerify: (String) -> Unit
) {
    val contactInfo = email
    val focusRequesters = remember { List(4) { FocusRequester() } }

    LaunchedEffect(Unit) {
        focusRequesters[0].requestFocus()
    }

    LaunchedEffect(otpValues.joinToString("")) {
        val otp = otpValues.joinToString("")
        if (otp.length == 4 && otp.all { it.isDigit() }) {
            kotlinx.coroutines.delay(100)
            onVerify(otp)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Surface(
            modifier = Modifier.size(80.dp),
            shape = RoundedCornerShape(24.dp),
            color = Brand50
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.MailOutline,
                    contentDescription = null,
                    tint = Brand600,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            "Verification Code",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = Slate900
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "Enter the 4-digit code sent to",
            textAlign = TextAlign.Center,
            fontSize = 15.sp,
            color = Slate500
        )
        Text(
            contactInfo,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = Brand600
        )
        
        Spacer(modifier = Modifier.height(40.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
        ) {
            otpValues.forEachIndexed { index, value ->
                OtpDigitField(
                    value = value,
                    onValueChange = { newValue ->
                        if (newValue.length > 1) {
                            val digitsOnly = newValue.filter { it.isDigit() }.take(4)
                            if (digitsOnly.isNotEmpty()) {
                                digitsOnly.forEachIndexed { i, char ->
                                    if (i < 4) otpValues[i] = char.toString()
                                }
                                val nextFocusIndex = digitsOnly.length.coerceAtMost(3)
                                focusRequesters[nextFocusIndex].requestFocus()
                            }
                        } else if (newValue.length == 1) {
                            otpValues[index] = newValue
                            if (index < 3) focusRequesters[index + 1].requestFocus()
                        } else {
                            otpValues[index] = ""
                        }
                    },
                    modifier = Modifier
                        .width(64.dp)
                        .focusRequester(focusRequesters[index])
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown && 
                                keyEvent.key == Key.Backspace && 
                                otpValues[index].isEmpty() && 
                                index > 0) {
                                focusRequesters[index - 1].requestFocus()
                                true
                            } else false
                        }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        if (resendTimer > 0) {
            Text(
                text = "Resend code in ${resendTimer}s",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Slate400
            )
        } else {
            Surface(
                onClick = onResend,
                color = Brand50,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Resend Verification Code",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Brand600
                )
            }
        }
    }
}

@Composable
fun OtpDigitField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    LaunchedEffect(value) {
        if (value != textFieldValue.text) {
            textFieldValue = TextFieldValue(text = value, selection = TextRange(value.length))
        }
    }

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { newFieldValue ->
            val updatedSelection = TextRange(newFieldValue.text.length)
            textFieldValue = newFieldValue.copy(selection = updatedSelection)
            if (newFieldValue.text != value) onValueChange(newFieldValue.text)
        },
        modifier = modifier
            .aspectRatio(1f)
            .onFocusChanged { if (it.isFocused) textFieldValue = textFieldValue.copy(selection = TextRange(textFieldValue.text.length)) },
        shape = RoundedCornerShape(16.dp),
        textStyle = androidx.compose.ui.text.TextStyle(
            textAlign = TextAlign.Center,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = Slate900
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        colors = AppUtils.standardOutlinedTextFieldColors()
    )
}

@Composable
fun NewPasswordContent(
    onPasswordValid: (Boolean) -> Unit,
    onPasswordChange: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val hasMinLength = password.length >= 8
    val hasUppercase = password.any { it.isUpperCase() }
    val hasNumber = password.any { it.isDigit() }
    val hasSpecialChar = password.any { !it.isLetterOrDigit() }

    val isValid = hasMinLength && hasUppercase && hasNumber && hasSpecialChar && 
                  password == confirmPassword && password.isNotEmpty()

    LaunchedEffect(isValid, password) {
        onPasswordValid(isValid)
        onPasswordChange(password)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Surface(
            modifier = Modifier.size(80.dp),
            shape = RoundedCornerShape(24.dp),
            color = Brand50
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint = Brand600,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            "New Password",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = Slate900
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "Please create a strong password that you don't use on other websites.",
            textAlign = TextAlign.Center,
            fontSize = 15.sp,
            color = Slate500,
            lineHeight = 22.sp
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("New Password") },
                placeholder = { Text("••••••••") },
                leadingIcon = { Icon(Icons.Default.Lock, null, tint = Slate400) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = Slate400)
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                shape = RoundedCornerShape(16.dp),
                colors = AppUtils.standardOutlinedTextFieldColors()
            )

            Spacer(modifier = Modifier.height(16.dp))

            PasswordStrengthSection(
                hasMinLength = hasMinLength,
                hasUppercase = hasUppercase,
                hasNumber = hasNumber,
                hasSpecialChar = hasSpecialChar
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Confirm New Password") },
                placeholder = { Text("••••••••") },
                leadingIcon = { Icon(Icons.Default.Shield, null, tint = Slate400) },
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(16.dp),
                isError = confirmPassword.isNotEmpty() && password != confirmPassword,
                colors = AppUtils.standardOutlinedTextFieldColors()
            )
            if (confirmPassword.isNotEmpty() && password != confirmPassword) {
                Text(
                    "Passwords do not match",
                    color = Color(0xFFDC2626),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }
        }
    }
}

@Composable
fun SuccessContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(100.dp),
            shape = CircleShape,
            color = Color(0xFFDCFCE7)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(50.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            "Account Secured!",
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            color = Slate900,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Your password has been successfully reset. You can now use your new password to access your account.",
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            color = Slate600,
            lineHeight = 24.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PasswordRecoveryPreview() {
    NurseWearConnectTheme {
        PasswordRecoveryScreen({}, {})
    }
}
