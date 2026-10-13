from cadgen import step, stl
from lib.shapes import print_shape

@step(out="../STEP/battery_tray.step")
@stl(out="../tmp/cadgen_mesh/battery_tray.stl", mesh_tolerance=0.0003, mesh_angular_tolerance=0.15)
def battery_tray():
    return print_shape("battery_tray")

if __name__ == "__main__":
    battery_tray()
