from cadgen import step, stl
from lib.shapes import print_shape

@step(out="../STEP/calibration_coupon.step")
@stl(out="../tmp/cadgen_mesh/calibration_coupon.stl", mesh_tolerance=0.0003, mesh_angular_tolerance=0.15)
def calibration_coupon():
    return print_shape("calibration_coupon")

if __name__ == "__main__":
    calibration_coupon()
