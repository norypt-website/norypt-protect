FROM debian:bookworm-slim

ENV DEBIAN_FRONTEND=noninteractive
ENV LANG=C.UTF-8
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=/opt/jdk-17/bin:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:$PATH

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
      ca-certificates curl unzip git xz-utils && \
    rm -rf /var/lib/apt/lists/*

# Pin JDK 17
RUN curl -fsSL -o /tmp/jdk.tar.gz \
      "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.13%2B11/OpenJDK17U-jdk_x64_linux_hotspot_17.0.13_11.tar.gz" && \
    mkdir -p /opt && tar -xzf /tmp/jdk.tar.gz -C /opt && \
    mv /opt/jdk-17* /opt/jdk-17 && rm /tmp/jdk.tar.gz

# Pin Android cmdline-tools (commit-pinned)
RUN mkdir -p /opt/android-sdk/cmdline-tools && \
    curl -fsSL -o /tmp/cmdline-tools.zip \
      "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" && \
    unzip -q /tmp/cmdline-tools.zip -d /opt/android-sdk/cmdline-tools && \
    mv /opt/android-sdk/cmdline-tools/cmdline-tools /opt/android-sdk/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

# Pin SDK platforms + build-tools
RUN yes | sdkmanager --licenses && \
    sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

WORKDIR /workspace
