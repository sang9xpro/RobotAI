"""Export an existing, verified official TalkNet checkpoint; no model is downloaded implicitly.

Usage: python export_talknet.py --source /path/TalkNet-ASD --checkpoint /path/model
       --sha256 EXPECTED_SHA256 --output /path/talknet.onnx
Official source: https://github.com/TaoRuijie/TalkNet-ASD
"""
import argparse
import hashlib
import pathlib
import sys


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=pathlib.Path, required=True)
    parser.add_argument("--checkpoint", type=pathlib.Path, required=True)
    parser.add_argument("--sha256", required=True)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    args = parser.parse_args()
    if hashlib.sha256(args.checkpoint.read_bytes()).hexdigest() != args.sha256.lower():
        raise ValueError("Checkpoint SHA256 mismatch")
    sys.path.insert(0, str(args.source.resolve()))
    import numpy as np
    import onnxruntime as ort
    import torch
    from model.talkNetModel import talkNetModel

    class Export(torch.nn.Module):
        def __init__(self):
            super().__init__()
            self.model = talkNetModel()
            self.fc = torch.nn.Linear(256, 2)

        def forward(self, audio, visual):
            a = self.model.forward_audio_frontend(audio)
            v = self.model.forward_visual_frontend(visual)
            a, v = self.model.forward_cross_attention(a, v)
            return self.fc(self.model.forward_audio_visual_backend(a, v))

    state = torch.load(args.checkpoint, map_location="cpu", weights_only=True)
    state = {key.removeprefix("module."): value for key, value in state.items()}
    network = Export().eval()
    model_state = {key.removeprefix("model."): value for key, value in state.items() if key.startswith("model.")}
    network.model.load_state_dict(model_state, strict=True)
    network.fc.load_state_dict({"weight": state["lossAV.FC.weight"], "bias": state["lossAV.FC.bias"]})
    torch.manual_seed(7)
    audio = torch.randn(1, 100, 13)
    visual = torch.rand(1, 25, 112, 112) * 255
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with torch.no_grad():
        reference = network(audio, visual).numpy()
        torch.onnx.export(network, (audio, visual), str(args.output), opset_version=17,
                          input_names=["audio", "visual"], output_names=["logits"], dynamo=False)
    session = ort.InferenceSession(str(args.output), providers=["CPUExecutionProvider"])
    actual = session.run(None, {"audio": audio.numpy(), "visual": visual.numpy()})[0]
    np.testing.assert_allclose(actual, reference, rtol=2e-4, atol=2e-4)
    print("Export parity passed; ONNX SHA256:", hashlib.sha256(args.output.read_bytes()).hexdigest())


if __name__ == "__main__":
    main()
