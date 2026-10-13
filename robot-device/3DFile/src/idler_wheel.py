from cadgen import step, stl
from lib.shapes import print_shape

@step(out="../STEP/idler_wheel.step")
@stl(out="../tmp/cadgen_mesh/idler_wheel.stl", mesh_tolerance=0.0003, mesh_angular_tolerance=0.15)
def idler_wheel():
    return print_shape("idler_wheel")

if __name__ == "__main__":
    idler_wheel()
