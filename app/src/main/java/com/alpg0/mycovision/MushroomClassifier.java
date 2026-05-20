package com.alpg0.mycovision;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class MushroomClassifier {

    private final Interpreter interpreter;
    private final int inputWidth;
    private final int inputHeight;
    private final int inputChannels;
    private final int numClasses;

    public static class PredictionResult {
        public final int predictedIndex;
        public final float confidence;

        public PredictionResult(int predictedIndex, float confidence) {
            this.predictedIndex = predictedIndex;
            this.confidence = confidence;
        }
    }

    public MushroomClassifier(Context context) throws IOException {
        MappedByteBuffer modelBuffer = loadModelFile(context, "model_unquant.tflite");
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4);

        interpreter = new Interpreter(modelBuffer, options);

        int[] inputShape = interpreter.getInputTensor(0).shape();
        int[] outputShape = interpreter.getOutputTensor(0).shape();

        inputHeight = inputShape[1];
        inputWidth = inputShape[2];
        inputChannels = inputShape[3];
        numClasses = outputShape[1];
    }

    private MappedByteBuffer loadModelFile(Context context, String modelName) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelName);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public PredictionResult classify(Bitmap bitmap) {
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true);

        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(4 * inputWidth * inputHeight * inputChannels);
        inputBuffer.order(ByteOrder.nativeOrder());

        for (int y = 0; y < inputHeight; y++) {
            for (int x = 0; x < inputWidth; x++) {
                int pixel = resized.getPixel(x, y);

                float r = ((pixel >> 16) & 0xFF) / 255.0f;
                float g = ((pixel >> 8) & 0xFF) / 255.0f;
                float b = (pixel & 0xFF) / 255.0f;

                inputBuffer.putFloat(r);
                inputBuffer.putFloat(g);
                inputBuffer.putFloat(b);
            }
        }

        inputBuffer.rewind();

        float[][] output = new float[1][numClasses];
        interpreter.run(inputBuffer, output);

        if (resized != bitmap) resized.recycle();

        int maxIndex = 0;
        float maxScore = output[0][0];

        for (int i = 1; i < numClasses; i++) {
            if (output[0][i] > maxScore) {
                maxScore = output[0][i];
                maxIndex = i;
            }
        }

        return new PredictionResult(maxIndex, maxScore);
    }

    public void close() {
        interpreter.close();
    }
}