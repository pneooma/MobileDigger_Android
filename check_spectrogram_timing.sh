#!/bin/bash

# Script to check spectrogram analysis timing from Android logs
# Usage: ./check_spectrogram_timing.sh

echo "Checking spectrogram analysis timing from Android logs..."
echo "=================================================="

# Set adb path
ADB_PATH="/Users/alinflorescu/Library/Android/sdk/platform-tools/adb"

# Check if adb is available
if [ ! -f "$ADB_PATH" ]; then
    echo "Error: adb not found at $ADB_PATH. Please check your Android SDK installation."
    exit 1
fi

# Check if device is connected
if ! $ADB_PATH devices | grep -q "device$"; then
    echo "Error: No Android device connected. Please connect your device and enable USB debugging."
    exit 1
fi

echo "Connected device:"
$ADB_PATH devices

echo ""
echo "Recent spectrogram timing logs:"
echo "==============================="

# Get recent logs related to spectrogram timing
$ADB_PATH logcat -d | grep -E "(Spectrogram|AudioManager.*time|generation.*time)" | tail -20

echo ""
echo "To see real-time logs, run: $ADB_PATH logcat | grep -E '(Spectrogram|AudioManager.*time)'"
echo ""
echo "To clear logs and start fresh: $ADB_PATH logcat -c"
