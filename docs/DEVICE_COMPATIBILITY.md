# Device compatibility

## Tesla target probe

The first target device was inspected through the existing paired ADB path
before the Android minimum version was selected.

| Property | Result |
| --- | --- |
| Android release | 14 |
| API level | 34 |
| Primary ABI | `armeabi-v7a` |
| Android TV features | Leanback and television device type declared |
| Physical panel | 3840 × 2160 |
| Android UI surface | 1920 × 1080 at 320 dpi |

The application uses `minSdk 26`. This keeps the receiver compatible with the
target device while leaving room for older Android TV boxes. The initial
release contains no ABI-specific native code.

Private addresses, ports, device identifiers, and pairing data are deliberately
excluded.
