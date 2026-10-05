FROM debian:bookworm-slim

ENV DEBIAN_FRONTEND=noninteractive
ENV LANG=C.UTF-8
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=/opt/jdk-17/bin:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:$PATH

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
      ca-certificates curl unzip git xz-utils && \
    rm -rf /var/lib/apt/lists/*

# Pin JDK 17 (Temurin 17.0.13+11), verified against Adoptium's published SHA-256
# (the release's .sha256.txt, same value in api.adoptium.net).
ARG JDK_SHA256=8682892fc02965930b9022c066fa164dd6f458ef4a5dc262016aa28333b30f49
RUN curl -fsSL -o /tmp/jdk.tar.gz \
      "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.13%2B11/OpenJDK17U-jdk_x64_linux_hotspot_17.0.13_11.tar.gz" && \
    echo "${JDK_SHA256}  /tmp/jdk.tar.gz" | sha256sum -c - && \
    mkdir -p /opt && tar -xzf /tmp/jdk.tar.gz -C /opt && \
    mv /opt/jdk-17* /opt/jdk-17 && rm /tmp/jdk.tar.gz

# Pin Android cmdline-tools 11076708. Google's repository2-3.xml publishes only a SHA-1 for
# this archive (checked first); the SHA-256 was computed from the download that matched it.
ARG CMDLINE_TOOLS_SHA1=d313adb7aedccf6cf0cfca51ec180f0059f5f8f8
ARG CMDLINE_TOOLS_SHA256=2d2d50857e4eb553af5a6dc3ad507a17adf43d115264b1afc116f95c92e5e258
RUN mkdir -p /opt/android-sdk/cmdline-tools && \
    curl -fsSL -o /tmp/cmdline-tools.zip \
      "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" && \
    echo "${CMDLINE_TOOLS_SHA1}  /tmp/cmdline-tools.zip" | sha1sum -c - && \
    echo "${CMDLINE_TOOLS_SHA256}  /tmp/cmdline-tools.zip" | sha256sum -c - && \
    unzip -q /tmp/cmdline-tools.zip -d /opt/android-sdk/cmdline-tools && \
    mv /opt/android-sdk/cmdline-tools/cmdline-tools /opt/android-sdk/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

# Pin SDK platforms + build-tools (sdkmanager checks each one against the checksum in
# Google's repository index)
RUN yes | sdkmanager --licenses && \
    sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

WORKDIR /workspace
