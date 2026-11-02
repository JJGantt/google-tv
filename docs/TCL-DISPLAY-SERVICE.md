# TCL display service (`tcl_tv_display`)

The picture menu's values (Brightness, Contrast, Black Level, picture mode, …) are not Android
settings. They live in MediaTek's picture-quality store behind a TCL binder service, and the
Settings app is only a front end to it. Diffing every `settings list` table before and after
moving the Brightness slider shows no change, which is how that was established.

    service name:  tcl_tv_display
    interface:     com.tcl.tv.display.ITvDisplayManagerService
    defined in:    /system_ext/framework/com.tcl.tv.display.jar  (197 transactions)

From the adb shell it is callable with `service call`:

    service call tcl_tv_display 101                      # getBacklight()
    service call tcl_tv_display 100 i32 0 i32 40 i32 2   # setBacklight(type, value, act)

A reply is `Parcel(00000000 <value> ...)`: the first word is the binder status (0 = ok), the
second the return value (hex). `setBacklight` returns 1 for accepted.

## Naming trap

TCL's menu label **Brightness is the backlight** (`getBacklight`/`setBacklight`). The service's
`getBrightness`/`setBrightness` (tx 30/29) are the menu's **Black Level** (it read 50, matching
the menu). Using the obvious-sounding pair changes the wrong setting.

## Argument enums

From `com.tcl.tv.pq.EnApplyType` and `EnTclActType`:

| arg | 0 | 1 | 2 |
|---|---|---|---|
| applyType | current picture mode | all picture modes | — |
| act | apply only | save only | apply and save |

`act=2` is what the menu does; `act=0` changes the panel without persisting.

## Transaction codes

The codes are the order of methods in the AIDL and will shift if a firmware update adds methods.
`bin/tv` (`DISPLAY_JAR_SHA256`) and the companion app (`Display.JAR_SHA256`) hold the SHA-256 of the
jar the codes were derived from and refuse every
picture call when the TV's jar differs, so an update stops brightness and contrast loudly instead of
changing some other setting. Re-derive the codes, then update the hash:

    adb pull /system_ext/framework/com.tcl.tv.display.jar && unzip -o com.tcl.tv.display.jar classes.dex
    jadx -d out classes.dex
    grep -n "TRANSACTION_" out/sources/com/tcl/tv/display/ITvDisplayManagerService.java

| code | method |
|---|---|
| 26 / 27 | setPictureMode / getPictureMode |
| 29 / 30 | setBrightness / getBrightness (Black Level; takes a window id, 0) |
| 31 / 32 | setContrast / getContrast `(window, type, value, act)` / `(window)` |
| 47 / 48 | setDynamicContrast / getDynamicContrast (0 off, 2 High) |
| 100 / 101 | setBacklight / getBacklight (menu "Brightness") |
| 108 / 109 | setLowBlueRay `(window, value, act)` / getLowBlueRay (reads 1 here) |
| 182 | setPictureModeByType |
| 172 / 173 | getHdrMode / setHdrMode |

Exercised: 100/101, 30, 31/32, 47/48, and the 109 read.
