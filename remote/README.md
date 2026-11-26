# MobileDigger Remote

Control MobileDigger on your phone from a computer next to you, over Bluetooth (no internet).

## Phone
Settings → General → **Desk remote (Bluetooth)** → switch on. Note the 6-digit PIN.

## Computer: extension (global hotkeys + mini player)
1. Unzip `MobileDigger-Remote-extension.zip` to a folder you keep.
2. Chrome: `chrome://extensions` (Edge: `edge://extensions`) → turn on **Developer mode** → **Load unpacked** → pick the folder.
3. The Remote tab opens. Click **Connect to phone**, pick the phone, type the PIN.
4. Click **Open mini player** for the always-on-top window.
5. Global shortcuts (work in any app while the Remote tab stays open):
   Ctrl+Shift+3 next · Ctrl+Shift+1 previous · Ctrl+Shift+8 like · Ctrl+Shift+9 dislike.
   Change them (and set Play/Pause) with **Change global shortcuts…**; keep the scope on **Global**.

## Computer: page only (no install)
Open `MobileDigger-Remote.html` in Chrome or Edge. Same remote and mini player, but keys only work
while the tab or the mini player is in front.

Files: `extension/` is the source; `build.py` regenerates the stand-alone page from it.
