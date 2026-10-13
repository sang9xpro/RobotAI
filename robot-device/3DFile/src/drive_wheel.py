from cadgen import step, stl
from lib.shapes import print_shape

@step(out="../STEP/drive_wheel.step")
@stl(out="../tmp/cadgen_mesh/drive_wheel.stl", mesh_tolerance=0.0003, mesh_angular_tolerance=0.15)
def drive_wheel():
    return print_shape("drive_wheel")

if __name__ == "__main__":
    drive_wheel()
