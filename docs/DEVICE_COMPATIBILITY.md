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

## Observed MVP matrix

The following results are physical observations from the first Tesla target,
not general claims about every device or codec.

| Area | Observed result |
| --- | --- |
| Representative 1920 × 1080 scene | Pass |
| Representative 3840 × 2160 scene | Pass |
| D-pad play/pause and ten-second seek | Pass |
| Replace the active scene without restarting the app | Pass |
| HOME, then reopen active playback | Pass |
| Start playback above Android TV Ambient Mode | Pass |
| Android process recreation | Automated recovery coverage; restores paused |

The exact containers and video/audio codecs of the representative private
media were not recorded, so this matrix deliberately makes no codec-wide
compatibility claim. Unsupported formats must surface an actionable playback
error. Additional devices and media formats stay unverified until physically
tested and recorded here.

Private addresses, ports, device identifiers, and pairing data are deliberately
excluded.
