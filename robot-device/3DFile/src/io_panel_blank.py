from cadgen import step, stl
from lib.shapes import print_shape

@step(out="../STEP/io_panel_blank.step")
@stl(out="../tmp/cadgen_mesh/io_panel_blank.stl", mesh_tolerance=0.0003, mesh_angular_tolerance=0.15)
def io_panel_blank():
    return print_shape("io_panel_blank")

if __name__ == "__main__":
    io_panel_blank()
