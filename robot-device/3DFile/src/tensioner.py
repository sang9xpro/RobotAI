from cadgen import step, stl
from lib.shapes import print_shape

@step(out="../STEP/tensioner.step")
@stl(out="../tmp/cadgen_mesh/tensioner.stl", mesh_tolerance=0.0003, mesh_angular_tolerance=0.15)
def tensioner():
    return print_shape("tensioner")

if __name__ == "__main__":
    tensioner()
