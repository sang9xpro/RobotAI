from cadgen import step, stl
from lib.shapes import print_shape

@step(out="../STEP/io_panel.step")
@stl(out="../tmp/cadgen_mesh/io_panel.stl", mesh_tolerance=0.0003, mesh_angular_tolerance=0.15)
def io_panel():
    return print_shape("io_panel")

if __name__ == "__main__":
    io_panel()
