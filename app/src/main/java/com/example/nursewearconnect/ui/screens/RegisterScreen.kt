package com.example.nursewearconnect.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nursewearconnect.ui.theme.Brand100
import com.example.nursewearconnect.ui.theme.Brand200
import com.example.nursewearconnect.ui.theme.Brand50
import com.example.nursewearconnect.ui.theme.Brand500
import com.example.nursewearconnect.ui.theme.Brand600
import com.example.nursewearconnect.ui.theme.Brand700
import com.example.nursewearconnect.ui.theme.NurseWearConnectTheme
import com.example.nursewearconnect.ui.theme.Slate100
import com.example.nursewearconnect.ui.theme.Slate200
import com.example.nursewearconnect.ui.theme.Slate400
import com.example.nursewearconnect.ui.theme.Slate50
import com.example.nursewearconnect.ui.theme.Slate500
import com.example.nursewearconnect.ui.theme.Slate600
import com.example.nursewearconnect.ui.theme.Slate700
import com.example.nursewearconnect.ui.theme.Slate900
import com.example.nursewearconnect.utils.AppUtils
import com.example.nursewearconnect.ui.components.ShimmerPlaceholder
import com.example.nursewearconnect.ui.components.EmptyState
import com.example.nursewearconnect.ui.components.PasswordStrengthSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    onRegisterSuccess: (String, String, String, String, String, String?, String?, String?, String?) -> Unit,
    onNavigateToLogin: () -> Unit,
    onResendVerification: (String) -> Unit = {},
    isLoading: Boolean = false,
    externalError: String? = null,
    isExternalSuccess: Boolean = false,
    onUploadDocument: (ByteArray, String, (String) -> Unit) -> Unit = { _, _, _ -> }
) {
    var currentStep by remember { mutableIntStateOf(1) }
    
    // Step 1: Role & Identity
    var selectedRole by remember { mutableStateOf("student") }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    
    // Step 2: Vendor Details (Conditional)
    var businessName by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var businessDescription by remember { mutableStateOf("") }
    var licenseUrl by remember { mutableStateOf<String?>(null) }
    var isUploadingDoc by remember { mutableStateOf(false) }
    
    // Step 3: Security & Consent
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var agreeToTerms by remember { mutableStateOf(false) }
    var receiveUpdates by remember { mutableStateOf(false) }
    
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showTerms by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    // Real-time validation
    val isEmailValid = AppUtils.isEmail(email)
    val isPhoneValid = AppUtils.isKEPhone(phoneNumber)
    val isNameValid = fullName.length >= 3

    // Password validation logic
    val hasMinLength = password.length >= 8
    val hasUppercase = password.any { it.isUpperCase() }
    val hasNumber = password.any { it.isDigit() }
    val hasSpecialChar = password.any { !it.isLetterOrDigit() }
    val isPasswordValid = hasMinLength && hasUppercase && hasNumber && hasSpecialChar
    val passwordsMatch = password.isNotEmpty() && password == confirmPassword

    val canGoToNext = when (currentStep) {
        1 -> isNameValid && isEmailValid && isPhoneValid
        2 -> if (selectedRole == "vendor") {
            businessName.length >= 3 && location.isNotBlank() && licenseUrl != null
        } else true
        3 -> isPasswordValid && passwordsMatch && agreeToTerms
        else -> false
    }

    val handleRegister = {
        if (!isPasswordValid) {
            errorMessage = "Please meet all password requirements"
        } else if (password != confirmPassword) {
            errorMessage = "Passwords do not match"
        } else {
            errorMessage = null
            onRegisterSuccess(
                selectedRole,
                fullName,
                email,
                phoneNumber,
                password,
                if (selectedRole == "vendor") businessName else null,
                if (selectedRole == "vendor") location else null,
                if (selectedRole == "vendor") businessDescription else null,
                if (selectedRole == "vendor") licenseUrl else null
            )
        }
    }

    val displayError = externalError ?: errorMessage

    if (showTerms) {
        TermsOfServiceScreen(onBack = { showTerms = false })
    } else if (showPrivacy) {
        PrivacyPolicyScreen(onBack = { showPrivacy = false })
    } else if (isExternalSuccess) {
        RegistrationSuccessScreen(
            role = selectedRole,
            email = email,
            onBackToLogin = onNavigateToLogin,
            onResend = { onResendVerification(email) }
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Background decorations... (same as before)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Main Content Wrapper to limit width on large screens
                Column(
                    modifier = Modifier
                        .widthIn(max = 700.dp)
                        .fillMaxWidth()
                ) {
                    // Header
                    RegisterHeader(
                        currentStep = currentStep,
                        onBack = {
                            if (currentStep > 1) currentStep-- else onBack()
                        }
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 700.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Step Indicator
                    RegistrationStepper(currentStep = currentStep, totalSteps = 3)

                    Spacer(modifier = Modifier.height(24.dp))

                    // Content based on step
                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                            } else {
                                (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                            }
                        }, label = "step_transition"
                    ) { step ->
                        when (step) {
                            1 -> IdentityStep(
                                selectedRole = selectedRole,
                                onRoleChange = { selectedRole = it },
                                fullName = fullName,
                                onFullNameChange = { fullName = it },
                                email = email,
                                onEmailChange = { email = it },
                                phoneNumber = phoneNumber,
                                onPhoneNumberChange = { phoneNumber = it },
                                focusManager = focusManager,
                                isNameValid = isNameValid,
                                isEmailValid = isEmailValid,
                                isPhoneValid = isPhoneValid
                            )
                            2 -> if (selectedRole == "vendor") {
                                VendorDetailsStep(
                                    businessName = businessName,
                                    onBusinessNameChange = { businessName = it },
                                    location = location,
                                    onLocationChange = { location = it },
                                    description = businessDescription,
                                    onDescriptionChange = { businessDescription = it },
                                    licenseUrl = licenseUrl,
                                    isUploading = isUploadingDoc,
                                    onUploadDocument = { bytes, extension ->
                                        isUploadingDoc = true
                                        onUploadDocument(bytes, extension) { url ->
                                            licenseUrl = url
                                            isUploadingDoc = false
                                        }
                                    },
                                    focusManager = focusManager
                                )
                            }
else {
                                MeasurementCalloutStep(onNext = { currentStep = 3 })
                            }
                            3 -> SecurityStep(
                                password = password,
                                onPasswordChange = { password = it },
                                confirmPassword = confirmPassword,
                                onConfirmPasswordChange = { confirmPassword = it },
                                passwordVisible = passwordVisible,
                                onPasswordVisibleChange = { passwordVisible = it },
                                confirmPasswordVisible = confirmPasswordVisible,
                                onConfirmPasswordVisibleChange = { confirmPasswordVisible = it },
                                agreeToTerms = agreeToTerms,
                                onAgreeToTermsChange = { agreeToTerms = it },
                                receiveUpdates = receiveUpdates,
                                onReceiveUpdatesChange = { receiveUpdates = it },
                                onShowTerms = { showTerms = true },
                                onShowPrivacy = { showPrivacy = true },
                                hasMinLength = hasMinLength,
                                hasUppercase = hasUppercase,
                                hasNumber = hasNumber,
                                hasSpecialChar = hasSpecialChar,
                                passwordsMatch = passwordsMatch,
                                focusManager = focusManager
                            )
                        }
                    }

                    
                    Spacer(modifier = Modifier.height(32.dp))
                }

                // Footer
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shadowElevation = 16.dp
                ) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 700.dp)
                                .padding(24.dp)
                                .navigationBarsPadding()
                        ) {
                            Button(
                                onClick = {
                                    if (currentStep < 3) currentStep++ else handleRegister()
                                },
                                enabled = canGoToNext && !isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Brand600)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                } else {
                                    Text(
                                        if (currentStep < 3) "Continue" else "Create Account",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RegisterHeader(currentStep: Int, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Text(
            "Step $currentStep of 3",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun RegistrationStepper(currentStep: Int, totalSteps: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(totalSteps) { index ->
            val stepNum = index + 1
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (stepNum <= currentStep) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
}

@Composable
fun IdentityStep(
    selectedRole: String,
    onRoleChange: (String) -> Unit,
    fullName: String,
    onFullNameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager,
    isNameValid: Boolean,
    isEmailValid: Boolean,
    isPhoneValid: Boolean
) {
    Column {
        Text("Select your role", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoleItem(
                modifier = Modifier.weight(1f),
                title = "Student Nurse",
                icon = Icons.Default.School,
                isSelected = selectedRole == "student" || selectedRole == "nurse",
                onClick = { onRoleChange("student") },
                accentColor = MaterialTheme.colorScheme.primary,
                bgColor = MaterialTheme.colorScheme.primaryContainer
            )
            RoleItem(
                modifier = Modifier.weight(1f),
                title = "Vendor",
                icon = Icons.Default.Store,
                isSelected = selectedRole == "vendor",
                onClick = { onRoleChange("vendor") },
                accentColor = Color(0xFF3B82F6),
                bgColor = Color(0xFFDBEAFE)
            )
        }

        if (selectedRole == "student" || selectedRole == "nurse") {
            Spacer(modifier = Modifier.height(16.dp))
            var expanded by remember { mutableStateOf(false) }
            val roles = listOf("student", "nurse")
            
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedCard(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Badge, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (selectedRole == "student") "Student Nurse" else "Professional Nurse",
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            if (expanded) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.8f).background(Color.White)
                ) {
                    roles.forEach { role ->
                        DropdownMenuItem(
                            text = { Text(if (role == "student") "Student Nurse" else "Professional Nurse") },
                            onClick = {
                                onRoleChange(role)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        Text("Personal Information", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(16.dp))
        
        RegisterTextField(
            value = fullName,
            onValueChange = onFullNameChange,
            label = "Full Name",
            placeholder = "Jane Doe",
            icon = Icons.Default.Person,
            focusManager = focusManager,
            isError = !isNameValid && fullName.isNotEmpty()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        RegisterTextField(
            value = email,
            onValueChange = onEmailChange,
            label = "Email Address",
            placeholder = "jane@example.com",
            icon = Icons.Default.Email,
            keyboardType = KeyboardType.Email,
            focusManager = focusManager,
            isError = !isEmailValid && email.isNotEmpty()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        RegisterTextField(
            value = phoneNumber,
            onValueChange = onPhoneNumberChange,
            label = "Phone Number",
            placeholder = "0700 000 000",
            icon = Icons.Default.Phone,
            keyboardType = KeyboardType.Phone,
            focusManager = focusManager,
            isError = !isPhoneValid && phoneNumber.isNotEmpty()
        )
    }
}

@Composable
fun VendorDetailsStep(
    businessName: String,
    onBusinessNameChange: (String) -> Unit,
    location: String,
    onLocationChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    licenseUrl: String?,
    isUploading: Boolean,
    onUploadDocument: (ByteArray, String) -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val contentResolver = context.contentResolver
    
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            val extension = when (contentResolver.getType(it)) {
                "application/pdf" -> "pdf"
                "image/png" -> "png"
                "image/jpeg" -> "jpeg"
                else -> "jpg"
            }
            val inputStream = contentResolver.openInputStream(it)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            if (bytes != null) {
                onUploadDocument(bytes, extension)
            }
        }
    }

    Column {
        Text("Business Details", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp)
        Text("Tell us more about your tailoring services.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(24.dp))

        RegisterTextField(
            value = businessName,
            onValueChange = onBusinessNameChange,
            label = "Business Name",
            placeholder = "Baraton Tailors",
            icon = Icons.Default.Store,
            focusManager = focusManager
        )

        Spacer(modifier = Modifier.height(16.dp))

        RegisterTextField(
            value = location,
            onValueChange = onLocationChange,
            label = "Location",
            placeholder = "Baraton Center, Nandi",
            icon = Icons.Default.LocationOn,
            focusManager = focusManager
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Verification Documents", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(8.dp))
        
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isUploading) { 
                    launcher.launch("*/*")
                },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (licenseUrl != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (licenseUrl != null) Icons.Default.CheckCircle else Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = if (licenseUrl != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (licenseUrl != null) "License Uploaded" else "Upload Business License (PDF/JPG/PNG)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (licenseUrl != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    if (licenseUrl == null) {
                        Text("Required for account approval", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Business Description", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            placeholder = { Text("Tell us about your services...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            shape = RoundedCornerShape(16.dp),
            minLines = 3,
            colors = AppUtils.standardOutlinedTextFieldColors()
        )
    }
}

@Composable
fun MeasurementCalloutStep(onNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Straighten,
            contentDescription = null,
            tint = Brand600,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Perfect Fit Guaranteed",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "NurseWear Connect allows you to save your body measurements. " +
            "You can skip this for now and add them later in your profile.",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        Surface(
            color = Brand50,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Tip: Having your measurements ready helps vendors provide accurate tailoring.",
                modifier = Modifier.padding(16.dp),
                fontSize = 13.sp,
                color = Brand700,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SecurityStep(
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onPasswordVisibleChange: (Boolean) -> Unit,
    confirmPasswordVisible: Boolean,
    onConfirmPasswordVisibleChange: (Boolean) -> Unit,
    agreeToTerms: Boolean,
    onAgreeToTermsChange: (Boolean) -> Unit,
    receiveUpdates: Boolean,
    onReceiveUpdatesChange: (Boolean) -> Unit,
    onShowTerms: () -> Unit,
    onShowPrivacy: () -> Unit,
    hasMinLength: Boolean,
    hasUppercase: Boolean,
    hasNumber: Boolean,
    hasSpecialChar: Boolean,
    passwordsMatch: Boolean,
    focusManager: androidx.compose.ui.focus.FocusManager
) {
    Column {
        Text("Security", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(24.dp))

        Text("Password", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            placeholder = { Text("••••••••", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            leadingIcon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingIcon = {
                IconButton(onClick = { onPasswordVisibleChange(!passwordVisible) }) {
                    Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            isError = !hasMinLength && password.isNotEmpty(),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            shape = RoundedCornerShape(16.dp),
            colors = AppUtils.standardOutlinedTextFieldColors()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Confirm Password", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            placeholder = { Text("••••••••", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            leadingIcon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingIcon = {
                IconButton(onClick = { onConfirmPasswordVisibleChange(!confirmPasswordVisible) }) {
                    Icon(if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            isError = !passwordsMatch && confirmPassword.isNotEmpty(),
            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            shape = RoundedCornerShape(16.dp),
            colors = AppUtils.standardOutlinedTextFieldColors()
        )
        
        if (!passwordsMatch && confirmPassword.isNotEmpty()) {
            Text(
                "Passwords do not match",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }

        PasswordStrengthSection(hasMinLength, hasUppercase, hasNumber, hasSpecialChar)

        Spacer(modifier = Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = agreeToTerms, onCheckedChange = onAgreeToTermsChange, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
            val annotatedString = buildAnnotatedString {
                append("I agree to the ")
                pushStringAnnotation("terms", "terms")
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) { append("Terms") }
                pop()
                append(" and ")
                pushStringAnnotation("privacy", "privacy")
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) { append("Privacy Policy") }
                pop()
            }
            ClickableText(
                text = annotatedString,
                style = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
                onClick = { offset ->
                    annotatedString.getStringAnnotations("terms", offset, offset).firstOrNull()?.let { onShowTerms() }
                    annotatedString.getStringAnnotations("privacy", offset, offset).firstOrNull()?.let { onShowPrivacy() }
                }
            )
        }
    }
}

@Composable
fun RegisterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    focusManager: androidx.compose.ui.focus.FocusManager,
    isError: Boolean = false
) {
    Column {
        Text(
            label, 
            fontSize = 14.sp, 
            fontWeight = FontWeight.Medium, 
            color = if (isError) MaterialTheme.colorScheme.error else Slate700
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            leadingIcon = { 
                Icon(
                    icon, 
                    null, 
                    tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                ) 
            },
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            colors = AppUtils.standardOutlinedTextFieldColors(),
            isError = isError,
            singleLine = true
        )
    }
}

@Composable
fun RoleItem(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    accentColor: Color,
    bgColor: Color
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(96.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 2.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else bgColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RegistrationSuccessScreen(role: String, email: String, onBackToLogin: () -> Unit, onResend: () -> Unit) {
    val isVendor = role.lowercase() == "vendor"
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(Brand100, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isVendor) Icons.Default.HourglassEmpty else Icons.Default.Email,
                null,
                tint = Brand600,
                modifier = Modifier.size(48.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            if (isVendor) "Registration Received!" else "Verify Your Email",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Slate900
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            if (isVendor) {
                "Your vendor account is currently pending verification. " +
                "Our admin team will review your business details and approve your account within 24-48 hours. " +
                "Please check $email for a confirmation link to verify your identity in the meantime."
            } else {
                "We've sent a verification link to $email. " +
                "Please click the link in the email to activate your account and start using NurseWear Connect."
            },
            fontSize = 16.sp,
            color = Slate600,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onBackToLogin,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Brand600)
        ) {
            Text("Back to Login", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        TextButton(
            onClick = onResend,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Didn't receive an email? Resend", color = Brand600, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun LegalScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White, CircleShape)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Slate400,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Text(
                    title,
                    modifier = Modifier.padding(start = 16.dp),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
                    .background(Color.White, RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun TermsOfServiceScreen(onBack: () -> Unit) {
    val date = java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date())
    LegalScreen(
        title = "Terms of Service",
        onBack = onBack
    ) {
        Text("NurseWear Connect (NWC)", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Brand600)
        Text("Effective Date: $date", fontSize = 14.sp, color = Slate500, modifier = Modifier.padding(vertical = 8.dp))
        
        LegalSection("1. Introduction", "Welcome to NurseWear Connect (NWC), a digital platform designed to connect student nurses with verified uniform vendors for the purpose of browsing, customizing, ordering, and purchasing nursing uniforms. By accessing or using this platform, you agree to comply with and be bound by these Terms of Service. If you do not agree, you must not use the system.")
        LegalSection("2. Definitions", "• Platform refers to NurseWear Connect (mobile and web applications).\n• User refers to any individual accessing the system.\n• Student refers to a registered nursing student using the platform.\n• Vendor refers to a registered tailor or uniform seller.\n• Admin refers to authorized system administrators managing the platform.")
        LegalSection("3. Eligibility", "Users must:\n• Be at least 18 years old or have appropriate institutional authorization\n• Provide accurate and complete registration information\n• Use the platform only for lawful purposes")
        LegalSection("4. User Accounts", "• Users are responsible for maintaining the confidentiality of their login credentials\n• Users must provide accurate and up-to-date information\n• The system reserves the right to suspend or terminate accounts that violate these terms")
        LegalSection("5. Vendor Verification", "• Vendors must undergo an approval process before accessing full system features\n• The platform reserves the right to approve, reject, or suspend vendors at its discretion\n• Vendors are responsible for the accuracy of their product listings and services")
        LegalSection("6. Orders and Transactions", "• All orders placed through the platform are binding once confirmed\n• Prices and product descriptions are provided by vendors and may vary\n• The platform facilitates transactions but does not manufacture or physically supply uniforms")
        LegalSection("7. Payments", "• Payments must be made through approved payment methods integrated into the platform\n• Users agree to provide accurate payment information\n• The platform is not responsible for payment failures caused by third-party providers")
        LegalSection("8. Reviews and Ratings", "• Users may leave reviews based on genuine experiences\n• False, misleading, or abusive reviews are prohibited\n• The platform reserves the right to remove inappropriate content")
        LegalSection("9. User Conduct", "Users agree NOT to:\n• Provide false or misleading information\n• Engage in fraudulent activities\n• Abuse, harass, or threaten other users\n• Attempt to compromise system security")
        LegalSection("10. Intellectual Property", "All content on the platform, including logos, system design, and software, is the property of NurseWear Connect or its licensors and is protected by applicable intellectual property laws.")
        LegalSection("11. Limitation of Liability", "• The platform acts as an intermediary between students and vendors\n• NurseWear Connect is not liable for:\n  o Product quality issues\n  o Delivery delays\n  o Disputes between users and vendors\n• Users engage with vendors at their own risk")
        LegalSection("12. Termination", "The platform reserves the right to:\n• Suspend or terminate user accounts for violations\n• Remove content that breaches these terms\n• Restrict access without prior notice if necessary")
        LegalSection("13. Changes to Terms", "These Terms of Service may be updated at any time. Continued use of the platform constitutes acceptance of the updated terms.")
        LegalSection("14. Governing Law", "These terms shall be governed by the laws of Kenya.")
        LegalSection("15. Contact Information", "For inquiries regarding these Terms:\n• Email: nwc-support@baraton.ac.ke\n• Institution: University of Eastern Africa, Baraton")
    }
}

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val date = java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date())
    LegalScreen(
        title = "Privacy Policy",
        onBack = onBack
    ) {
        Text("NurseWear Connect (NWC)", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Brand600)
        Text("Effective Date: $date", fontSize = 14.sp, color = Slate500, modifier = Modifier.padding(vertical = 8.dp))

        LegalSection("1. Introduction", "NurseWear Connect (NWC) is committed to protecting the privacy and personal data of its users. This Privacy Policy explains how we collect, use, and safeguard your information.")
        LegalSection("2. Information We Collect", "We may collect the following data:\na) Personal Information: Full name, Email address, Phone number, Password (encrypted)\nb) Vendor Information: Business name, Location, Service descriptions\nc) Transaction Data: Orders and purchase history, Payment confirmations\nd) Communication Data: Chat messages between users and vendors")
        LegalSection("3. How We Use Information", "We use collected data to:\n• Create and manage user accounts\n• Process orders and transactions\n• Facilitate communication between users\n• Improve system functionality and user experience\n• Ensure system security and prevent fraud")
        LegalSection("4. Data Sharing", "We do NOT sell user data. However, data may be shared with:\n• Vendors (for order fulfillment)\n• Payment service providers (for transaction processing)\n• System administrators (for monitoring and support)")
        LegalSection("5. Data Security", "We implement security measures including:\n• Password encryption\n• Secure authentication (JWT)\n• SSL encryption for data transmission\nDespite these measures, no system is completely secure, and users share information at their own risk.")
        LegalSection("6. User Rights", "Users have the right to:\n• Access their personal data\n• Update or correct their information\n• Request deletion of their account\n• Withdraw consent where applicable")
        LegalSection("7. Data Retention", "• User data is retained as long as the account is active\n• Transaction records may be retained for legal and auditing purposes")
        LegalSection("8. Cookies and Tracking", "The platform may use cookies or similar technologies to:\n• Enhance user experience\n• Maintain login sessions\n• Analyze system usage")
        LegalSection("9. Third-Party Services", "The platform may integrate third-party services (e.g., payment gateways). These services have their own privacy policies, and we are not responsible for their practices.")
        LegalSection("10. Children’s Privacy", "The platform is not intended for users under the age of 18 without supervision or institutional authorization.")
        LegalSection("11. Changes to Privacy Policy", "We may update this Privacy Policy periodically. Users will be notified of significant changes where necessary.")
        LegalSection("12. Contact Information", "For privacy-related inquiries:\n• Email: sainska@proton.me\n• Institution: University of Eastern Africa, Baraton")
    }
}

@Composable
fun LegalSection(title: String, content: String) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Slate900)
        Spacer(modifier = Modifier.height(4.dp))
        Text(content, fontSize = 14.sp, color = Slate600, lineHeight = 20.sp)
    }
}

@Preview(showBackground = true)
@Composable
fun RegisterPreview() {
    NurseWearConnectTheme {
        RegisterScreen({}, { _, _, _, _, _, _, _, _, _ -> }, {})
    }
}
