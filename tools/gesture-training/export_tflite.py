#!/usr/bin/env python3
"""Export the exact trained softmax weights as a float32 TFLite head (69 -> classes).
The palm detector, features and temporal/ownership gates remain in the application.
Schema: https://github.com/tensorflow/tensorflow/blob/v2.18.0/tensorflow/lite/schema/schema.fbs
"""
import argparse
import json
import struct
from pathlib import Path


def export(model):
    import flatbuffers
    import tflite as schema
    builder = flatbuffers.Builder(8192)

    def ints(values):
        builder.StartVector(4, len(values), 4)
        for v in reversed(values):
            builder.PrependInt32(v)
        return builder.EndVector()

    def offsets(values):
        builder.StartVector(4, len(values), 4)
        for v in reversed(values):
            builder.PrependUOffsetTRelative(v)
        return builder.EndVector()

    def buffer(data=b""):
        contents = builder.CreateByteVector(data) if data else None
        schema.BufferStart(builder)
        if contents is not None:
            schema.BufferAddData(builder, contents)
        return schema.BufferEnd(builder)

    def floats(values):
        return struct.pack("<"+"f"*len(values), *values)

    classes = len(model["labels"])
    buffers = [buffer(), buffer(floats([v for row in model["weights"] for v in row])), buffer(floats(model["bias"]))]

    def tensor(name, shape, buffer_index=0):
        name_offset = builder.CreateString(name)
        shape_offset = ints(shape)
        schema.TensorStart(builder)
        schema.TensorAddShape(builder, shape_offset)
        schema.TensorAddType(builder, schema.TensorType.FLOAT32)
        schema.TensorAddBuffer(builder, buffer_index)
        schema.TensorAddName(builder, name_offset)
        return schema.TensorEnd(builder)

    tensors = [tensor("hand69", [1, 69]), tensor("weights", [classes, 69], 1),
               tensor("bias", [classes], 2), tensor("logits", [1, classes]), tensor("probabilities", [1, classes])]
    schema.FullyConnectedOptionsStart(builder)
    schema.FullyConnectedOptionsAddFusedActivationFunction(builder, schema.ActivationFunctionType.NONE)
    fc_options = schema.FullyConnectedOptionsEnd(builder)
    schema.SoftmaxOptionsStart(builder)
    schema.SoftmaxOptionsAddBeta(builder, 1.0)
    softmax_options = schema.SoftmaxOptionsEnd(builder)

    def operator(code, inputs, outputs, option_type, options):
        ins, outs = ints(inputs), ints(outputs)
        schema.OperatorStart(builder)
        schema.OperatorAddOpcodeIndex(builder, code)
        schema.OperatorAddInputs(builder, ins)
        schema.OperatorAddOutputs(builder, outs)
        schema.OperatorAddBuiltinOptionsType(builder, option_type)
        schema.OperatorAddBuiltinOptions(builder, options)
        return schema.OperatorEnd(builder)

    operators = [operator(0, [0, 1, 2], [3], schema.BuiltinOptions.FullyConnectedOptions, fc_options),
                 operator(1, [3], [4], schema.BuiltinOptions.SoftmaxOptions, softmax_options)]
    ts, ops, inputs, outputs = offsets(tensors), offsets(operators), ints([0]), ints([4])
    name = builder.CreateString("Ken trained gesture head")
    schema.SubGraphStart(builder)
    schema.SubGraphAddTensors(builder, ts)
    schema.SubGraphAddInputs(builder, inputs)
    schema.SubGraphAddOutputs(builder, outputs)
    schema.SubGraphAddOperators(builder, ops)
    schema.SubGraphAddName(builder, name)
    graph = schema.SubGraphEnd(builder)
    codes = []
    for op in (schema.BuiltinOperator.FULLY_CONNECTED, schema.BuiltinOperator.SOFTMAX):
        schema.OperatorCodeStart(builder)
        schema.OperatorCodeAddBuiltinCode(builder, op)
        schema.OperatorCodeAddDeprecatedBuiltinCode(builder, op)
        schema.OperatorCodeAddVersion(builder, 1)
        codes.append(schema.OperatorCodeEnd(builder))
    graph_vector, code_vector, buffer_vector = offsets([graph]), offsets(codes), offsets(buffers)
    description = builder.CreateString("Ken hand69-v1; labels and thresholds in companion model.json")
    schema.ModelStart(builder)
    schema.ModelAddVersion(builder, 3)
    schema.ModelAddSubgraphs(builder, graph_vector)
    schema.ModelAddOperatorCodes(builder, code_vector)
    schema.ModelAddBuffers(builder, buffer_vector)
    schema.ModelAddDescription(builder, description)
    root = schema.ModelEnd(builder)
    builder.Finish(root, file_identifier=b"TFL3")
    return bytes(builder.Output())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("model", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    artifact = json.loads(args.model.read_text())
    model = artifact["model"]
    if artifact["version"] != 1 or model["featureVersion"] != "ken-hand69-v1":
        parser.error("Incompatible model")
    args.output.write_bytes(export(model))
    print(f"Exported float32 head to {args.output}; import model.json into Ken")


if __name__ == "__main__":
    main()
