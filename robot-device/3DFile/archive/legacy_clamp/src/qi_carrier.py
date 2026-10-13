from cadgen import step, stl
from lib.shapes import print_shape

@step(out="../STEP/qi_carrier.step")
@stl(out="../tmp/cadgen_mesh/qi_carrier.stl", mesh_tolerance=0.0003, mesh_angular_tolerance=0.15)
def qi_carrier():
    return print_shape("qi_carrier")

if __name__ == "__main__":
    qi_carrier()
