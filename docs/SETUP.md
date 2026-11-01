# Setup

## On the TV (once)

Settings → System → About → click "Android TV OS build" seven times. Then Settings → System →
Developer options → **Wireless debugging** on. USB debugging is not needed.

## Pairing a machine

Each machine that controls the TV pairs separately; the Mac and the Pi are both paired.

    adb pair <ip>:<pairing-port> <six-digit code>

The code and pairing port come from Wireless debugging → "Pair device with pairing code", and
are valid only while that dialog is open.

**No one has to be at the TV** if any machine is already paired: drive the TV from that machine
to Developer options → Wireless debugging (`am start -a
android.settings.APPLICATION_DEVELOPMENT_SETTINGS`, then D-pad down to it), open the pairing
dialog, read the code and port with `uiautomator dump`, and run `adb pair` on the new machine.
Move focus with D-pad keys and check `focused="true"` before pressing Enter: the Wireless
debugging screen opens with focus on **Disabled**, and selecting it kills every connection.
Coordinate taps from `uiautomator` bounds do not land reliably on this screen; key presses do.

## Machine prerequisites

- **Mac:** `brew install --cask android-platform-tools`. Its adb resolves mDNS itself.
- **Pi:** `apt install adb avahi-utils`. Debian's adb is built without mDNS, so `bin/tv` finds the
  TV with `avahi-browse` instead. The adb server is ~5 MB resident while idle.
- **Both:** `~/.config/google-tv/` holds what is specific to this house and stays out of the repo:
  `mac` (the TV's wlan0 MAC, one line, for `bin/tv wake` and `power on`; from the router or
  `adb shell cat /sys/class/net/wlan0/address`) and `hub-token` (CAPABILITIES.md § Sidebar).

## Discovery

The TV's IP is DHCP and its connect port changes whenever wireless debugging is re-enabled.
`bin/tv` knows the TV only by its adb service name (`adb-D00650B30E80300FC-jOM1G6`), finds the
current `ip:port` from the `_adb-tls-connect._tcp` mDNS announcement, and caches it in
`~/.cache/google-tv-addr`.

## What can break the connection

- Wireless debugging switched off (by hand, or possibly by a reboot or network change; unmeasured).
  Pairing survives; only the toggle has to come back.
- "Revoke USB debugging authorizations" in Developer options also drops wireless pairings.
