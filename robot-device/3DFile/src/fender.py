from cadgen import step, stl
from lib.shapes import print_shape

@step(out="../STEP/fender.step")
@stl(out="../tmp/cadgen_mesh/fender.stl", mesh_tolerance=0.0003, mesh_angular_tolerance=0.15)
def fender():
    return print_shape("fender")

if __name__ == "__main__":
    fender()
