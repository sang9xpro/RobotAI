from cadgen import step, stl
from lib.shapes import print_shape

@step(out="../STEP/tower_side_panel.step")
@stl(out="../tmp/cadgen_mesh/tower_side_panel.stl", mesh_tolerance=0.0003, mesh_angular_tolerance=0.15)
def tower_side_panel():
    return print_shape("tower_side_panel")

if __name__ == "__main__":
    tower_side_panel()
