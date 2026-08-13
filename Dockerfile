# Use an official Android SDK build image
FROM openjdk:17-jdk-slim

# Set environment variables
ENV ANDROID_HOME /opt/android-sdk
ENV GRADLE_USER_HOME /app/.gradle
ENV PATH ${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools

# Install dependencies
RUN apt-get update && apt-get install -y \
    curl \
    unzip \
    git \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# Install Android SDK
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && curl -o sdk.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
    && unzip sdk.zip -d ${ANDROID_HOME}/cmdline-tools \
    && mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
    && rm sdk.zip

# Accept licenses and install platform tools
RUN yes | sdkmanager --licenses \
    && sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

# Set working directory
WORKDIR /app

# Copy project files and set permissions
COPY . .
RUN chmod +x gradlew

# Build the app and upload to Supabase
# This assumes the uploadApkToSupabase task is defined in app/build.gradle.kts
CMD ["./gradlew", "assembleDebug", "uploadApkToSupabase"]
