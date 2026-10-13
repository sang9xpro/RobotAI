from cadgen import step, stl
from lib.shapes import print_shape

@step(out="../STEP/neck_shroud.step")
@stl(out="../tmp/cadgen_mesh/neck_shroud.stl", mesh_tolerance=0.0003, mesh_angular_tolerance=0.15)
def neck_shroud():
    return print_shape("neck_shroud")

if __name__ == "__main__":
    neck_shroud()
