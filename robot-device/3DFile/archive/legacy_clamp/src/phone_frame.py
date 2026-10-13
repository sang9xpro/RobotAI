from cadgen import step, stl
from lib.shapes import print_shape

@step(out="../STEP/phone_frame.step")
@stl(out="../tmp/cadgen_mesh/phone_frame.stl", mesh_tolerance=0.0003, mesh_angular_tolerance=0.15)
def phone_frame():
    return print_shape("phone_frame")

if __name__ == "__main__":
    phone_frame()
