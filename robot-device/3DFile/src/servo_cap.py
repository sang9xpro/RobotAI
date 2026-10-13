from cadgen import step, stl
from lib.shapes import print_shape

@step(out="../STEP/servo_cap.step")
@stl(out="../tmp/cadgen_mesh/servo_cap.stl", mesh_tolerance=0.0003, mesh_angular_tolerance=0.15)
def servo_cap():
    return print_shape("servo_cap")

if __name__ == "__main__":
    servo_cap()
