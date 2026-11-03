#!/bin/bash
# Fills in the server URL and API key on the app's login screen, on a device reached over adb.
# Asks for both here (the key with hidden input) and leaves the focus on Submit: press OK on the remote.
# Nothing is saved, printed or passed as an argument. If the app is already logged in, nothing is changed.
# Some keyboards alter text sent over adb. If so, Gboard is used while typing, when the device has it,
# and the previous keyboard is put back at the end.
#
# Usage: scripts/fill-login.sh <device-ip>
set -u
set +x

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
PKG="io.github.adriamarzo.immichtv"
DUMP="/data/local/tmp/immich-login-ui.xml"
GBOARD="com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME"
ORIGINAL_IME=""

[ $# -eq 1 ] || { echo "Usage: $0 <device-ip>" >&2; exit 2; }
TV="$1:5555"

dev() { "$ADB" -s "$TV" shell "$@"; }
pause() { perl -e 'select(undef,undef,undef,$ARGV[0])' "${1:-1.2}"; }
fail() { echo "$1 Nothing else was sent." >&2; dev rm -f "$DUMP" >/dev/null 2>&1; exit 1; }

restore_keyboard() {
  [ -n "$ORIGINAL_IME" ] || return 0
  dev ime set "$ORIGINAL_IME" >/dev/null 2>&1
  dev ime disable "$GBOARD" >/dev/null 2>&1
  echo "Keyboard put back."
}
trap restore_keyboard EXIT

# The screen's text goes to the device's temp folder for a moment. A password field reads as dots.
screen_xml() {
  dev uiautomator dump "$DUMP" >/dev/null 2>&1
  dev cat "$DUMP" 2>/dev/null
  dev rm -f "$DUMP" >/dev/null 2>&1
}

# Step 1 has one action and its button, step 2 has three and its button. Not reliant on the language.
login_step() {
  case "$(screen_xml | grep -o 'guidedactions_item_title' | wc -l | tr -d ' ')" in
    2) echo 1 ;;
    4) echo 2 ;;
    *) echo 0 ;;
  esac
}

# Prints "<password> <text length>" for the field being edited, without printing its text.
edited_field() {
  screen_xml | python3 -I -c '
import re, sys
for n in re.findall(r"<node [^>]*>", sys.stdin.read()):
    if "EditText" in n and "focused=\"true\"" in n:
        print(re.search(r"password=\"(\w+)\"", n)[1], len(re.search(r" text=\"([^\"]*)\"", n)[1]))
        break
'
}

# Says whether the field being edited holds exactly the URL, without printing it.
url_arrived() {
  screen_xml | URL="$URL" python3 -I -c '
import html, os, re, sys
for n in re.findall(r"<node [^>]*>", sys.stdin.read()):
    if "EditText" in n and "focused=\"true\"" in n:
        print("yes" if html.unescape(re.search(r" text=\"([^\"]*)\"", n)[1]) == os.environ["URL"] else "no")
        break
'
}

# A fresh start, so both fields begin empty. Ends with the URL field open.
open_url_field() {
  dev am force-stop "$PKG"
  dev monkey -p "$PKG" -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null 2>&1
  pause 4
  dev dumpsys window | grep -m1 mCurrentFocus | grep -q "$PKG/" || fail "Immich did not come to the front."
  local step
  step=$(login_step)
  [ "$step" = 0 ] && fail "The login screen is not showing: Immich may already be set up."
  if [ "$step" = 1 ]; then
    dev input keyevent KEYCODE_DPAD_RIGHT; pause 0.8
    dev input keyevent KEYCODE_DPAD_CENTER; pause 2
    [ "$(login_step)" = 2 ] || fail "Could not get past the first login screen."
  fi
  dev input keyevent KEYCODE_DPAD_CENTER; pause 1.5
  [ "$(edited_field)" = "false 0" ] || fail "The URL field did not open empty."
}

type_url() {
  printf "input text '%s'\n" "$URL" | "$ADB" -s "$TV" shell sh >/dev/null 2>&1
  pause 2
  [ "$(url_arrived)" = yes ]
}

printf 'Server URL (https://...): '
read -r URL
[[ "$URL" =~ ^https://[A-Za-z0-9.-]+(:[0-9]+)?(/[A-Za-z0-9._~/-]*)?$ ]] ||
  { echo "That is not an https:// URL this script can send. Nothing was sent." >&2; exit 2; }
printf 'API key (input hidden): '
read -r -s KEY
printf '\n'
case "$KEY" in
  ''|*[!A-Za-z0-9]*) echo "The key is empty or has characters this script does not send. Nothing was sent." >&2; exit 2 ;;
esac

"$ADB" connect "$TV" >/dev/null 2>&1
[ "$("$ADB" -s "$TV" get-state 2>/dev/null)" = device ] || fail "Could not reach $TV over adb."

open_url_field
if ! type_url; then
  dev ime list -a -s | tr -d '\r' | grep -qx "$GBOARD" || fail "This device's keyboard alters the text and Gboard is not available."
  echo "This device's keyboard altered the text. Using Gboard while typing."
  ORIGINAL_IME=$(dev settings get secure default_input_method | tr -d '\r')
  dev ime enable "$GBOARD" >/dev/null 2>&1
  dev ime set "$GBOARD" >/dev/null 2>&1
  open_url_field
  type_url || fail "The URL did not reach the field correctly, even with Gboard."
fi

dev input keyevent KEYCODE_ENTER; pause
[ "$(edited_field)" = "true 0" ] || fail "The API key field did not open."

# Through stdin, so the key is not in this Mac's process list.
KEY_LENGTH=${#KEY}
printf 'input text %s\n' "$KEY" | "$ADB" -s "$TV" shell sh >/dev/null 2>&1
RC=$?
KEY=""
[ "$RC" = 0 ] || fail "Sending the key failed."
pause 2
[ "$(edited_field)" = "true $KEY_LENGTH" ] || fail "The key did not reach the field in full."
dev input keyevent KEYCODE_ENTER; pause
dev input keyevent KEYCODE_DPAD_RIGHT; pause 0.6

echo "Done. The focus should be on Submit: check the screen and press OK on the remote."
