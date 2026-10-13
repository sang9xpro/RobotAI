from cadgen import step, stl
from lib.shapes import print_shape

@step(out="../STEP/head_yoke.step")
@stl(out="../tmp/cadgen_mesh/head_yoke.stl", mesh_tolerance=0.0003, mesh_angular_tolerance=0.15)
def head_yoke():
    return print_shape("head_yoke")

if __name__ == "__main__":
    head_yoke()
