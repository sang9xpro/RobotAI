# MẦM R4 — model catalogue

All dimensions in mm. One decorated entrypoint per manufactured part. Active models:

- `base.py` → `STEP/base.step`, native printable `STL/base.stl` through `build.py`.
- `lid.py` → `STEP/lid.step`, native printable `STL/lid.stl` through `build.py`.
- `battery_tray.py` → `STEP/battery_tray.step`, native printable `STL/battery_tray.stl` through `build.py`.
- `electronics_deck.py` → `STEP/electronics_deck.step`, native printable `STL/electronics_deck.stl` through `build.py`.
- `motor_cradle.py` → `STEP/motor_cradle.step`, native printable `STL/motor_cradle.stl` through `build.py`.
- `motor_cap.py` → `STEP/motor_cap.step`, native printable `STL/motor_cap.stl` through `build.py`.
- `drive_wheel.py` → `STEP/drive_wheel.step`, native printable `STL/drive_wheel.stl` through `build.py`.
- `idler_wheel.py` → `STEP/idler_wheel.step`, native printable `STL/idler_wheel.stl` through `build.py`.
- `bearing_spacer.py` → `STEP/bearing_spacer.step`, native printable `STL/bearing_spacer.stl` through `build.py`.
- `tensioner.py` → `STEP/tensioner.step`, native printable `STL/tensioner.stl` through `build.py`.
- `track.py` → `STEP/track.step`, native printable `STL/track.stl` through `build.py`.
- `fender.py` → `STEP/fender.step`, native printable `STL/fender.stl` through `build.py`.
- `sensor_carrier.py` → `STEP/sensor_carrier.step`, native printable `STL/sensor_carrier.stl` through `build.py`.
- `front_sensor_carrier.py` → `STEP/front_sensor_carrier.step`, native printable `STL/front_sensor_carrier.stl` through `build.py`.
- `front_sensor_bezel.py` → `STEP/front_sensor_bezel.step`, native printable `STL/front_sensor_bezel.stl` through `build.py`.
- `io_panel.py` → `STEP/io_panel.step`, native printable `STL/io_panel.stl` through `build.py`.
- `io_panel_blank.py` → `STEP/io_panel_blank.step`, native printable `STL/io_panel_blank.stl` through `build.py`.
- `neck_mount.py` → `STEP/neck_mount.step`, native printable `STL/neck_mount.stl` through `build.py`.
- `servo_cap.py` → `STEP/servo_cap.step`, native printable `STL/servo_cap.stl` through `build.py`.
- `head_stem.py` → `STEP/head_stem.step`, native printable `STL/head_stem.stl` through `build.py`.
- `magnetic_head.py` → `STEP/magnetic_head.step`, native printable `STL/magnetic_head.stl` through `build.py`.
- `neck_shroud.py` → `STEP/neck_shroud.step`, native printable `STL/neck_shroud.stl` through `build.py`.
- `cable_clip.py` → `STEP/cable_clip.step`, native printable `STL/cable_clip.stl` through `build.py`.
- `calibration_coupon.py` → `STEP/calibration_coupon.step`, native printable `STL/calibration_coupon.stl` through `build.py`.

Assemblies: `assembly.py`, `internal_assembly.py`, `parts_layout.py`, `magnetic_mount_preview.py`.

Shape factories and print orientation: `lib/shapes.py`. Rigid placement: `lib/assembly_layout.py`. The left asymmetric track is mirrored across thickness, preserving front/rear. R3 wheel radii and center heights are explicit in factories/layout (rear 26/31, front 18/23); several legacy reference constants in params.py are not driving dimensions. Revalidate both geometry and layout when changing those dimensions.

Old clamp models are archived and excluded from the current build. Do not print every historical file in the workspace; follow docs/print_manifest.csv.

- `tower_side_panel.py` → `STEP/tower_side_panel.step`, `STL/tower_side_panel.stl`: print one original and one X-mirrored panel.
