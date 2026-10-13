from cadgen import step, stl
from lib.shapes import print_shape

@step(out="../STEP/electronics_deck.step")
@stl(out="../tmp/cadgen_mesh/electronics_deck.stl", mesh_tolerance=0.0003, mesh_angular_tolerance=0.15)
def electronics_deck():
    return print_shape("electronics_deck")

if __name__ == "__main__":
    electronics_deck()
