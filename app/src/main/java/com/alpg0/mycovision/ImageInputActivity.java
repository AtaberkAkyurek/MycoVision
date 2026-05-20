package com.alpg0.mycovision;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.alpg0.mycovision.databinding.ActivityImageInputBinding;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ImageInputActivity extends AppCompatActivity {

    private static final String FILE_PROVIDER_AUTHORITY = "com.alpg0.mycovision.fileprovider";

    private ActivityImageInputBinding binding;
    private Uri pendingCameraUri;
    private Uri selectedImageUri;
    private MushroomClassifier classifier;

    // ── Camera launcher ────────────────────────────────────────────────────
    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && pendingCameraUri != null) {
                    selectedImageUri = pendingCameraUri;
                    showPreview(selectedImageUri);
                } else {
                    Toast.makeText(this, "Camera cancelled.", Toast.LENGTH_SHORT).show();
                }
            });

    // ── Gallery launcher ───────────────────────────────────────────────────
    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    showPreview(selectedImageUri);
                }
            });

    // ── Camera permission launcher ─────────────────────────────────────────
    private final ActivityResultLauncher<String> cameraPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) launchCamera();
                else showPermissionDenied("Camera");
            });

    // ── Gallery permission launcher ────────────────────────────────────────
    private final ActivityResultLauncher<String> galleryPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) galleryLauncher.launch("image/*");
                else showPermissionDenied("Storage");
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityImageInputBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        try {
            classifier = new MushroomClassifier(this);
        } catch (IOException e) {
            Toast.makeText(this, "Failed to load model: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        binding.btnBackHome.setOnClickListener(v -> finish());

        binding.btnCamera.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                cameraPermLauncher.launch(Manifest.permission.CAMERA);
            }
        });

        binding.btnGallery.setOnClickListener(v -> {
            String perm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ? Manifest.permission.READ_MEDIA_IMAGES
                    : Manifest.permission.READ_EXTERNAL_STORAGE;
            if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
                galleryLauncher.launch("image/*");
            } else {
                galleryPermLauncher.launch(perm);
            }
        });

        binding.btnAnalyze.setOnClickListener(v -> {
            if (selectedImageUri != null) {
                analyzeImage(selectedImageUri);
            } else {
                Toast.makeText(this, "Please select or capture an image first.", Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnAnalyze.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (classifier != null) {
            classifier.close();
            classifier = null;
        }
    }

    private void launchCamera() {
        File photoFile = createImageFile();
        if (photoFile == null) {
            Toast.makeText(this, "Cannot create image file.", Toast.LENGTH_SHORT).show();
            return;
        }
        pendingCameraUri = FileProvider.getUriForFile(this, FILE_PROVIDER_AUTHORITY, photoFile);
        cameraLauncher.launch(pendingCameraUri);
    }

    private File createImageFile() {
        try {
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File dir = new File(getFilesDir(), "camera_temp");
            if (!dir.exists()) dir.mkdirs();
            return new File(dir, "CAPTURE_" + stamp + ".jpg");
        } catch (Exception e) {
            return null;
        }
    }

    private void showPreview(Uri uri) {
        binding.ivPreview.setImageURI(uri);
        binding.ivPreview.setVisibility(View.VISIBLE);
        binding.btnAnalyze.setVisibility(View.VISIBLE);
        binding.tvPickHint.setVisibility(View.GONE);
    }

    private void analyzeImage(Uri imageUri) {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnAnalyze.setEnabled(false);

        new Thread(() -> {
            try {
                InputStream stream = getContentResolver().openInputStream(imageUri);
                Bitmap bitmap = BitmapFactory.decodeStream(stream);
                if (stream != null) stream.close();

                if (bitmap == null) {
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.btnAnalyze.setEnabled(true);
                        Toast.makeText(this, "Could not decode image.", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                // Step 1: Verify image contains organic/nature content typical of mushroom photos
                InputImage mlImage = InputImage.fromBitmap(bitmap, 0);
                ImageLabeler labeler = ImageLabeling.getClient(
                        new ImageLabelerOptions.Builder()
                                .setConfidenceThreshold(0.10f)
                                .build());

                List<ImageLabel> labels = Tasks.await(labeler.process(mlImage));

                boolean isMushroomDetected = false;
                for (ImageLabel label : labels) {
                    String text = label.getText().toLowerCase();
                    if (text.contains("mushroom") || text.contains("fungus") || text.contains("fungi")
                            || text.contains("agaric") || text.contains("toadstool")
                            || text.contains("bolete") || text.contains("chanterelle")
                            || text.contains("truffle") || text.contains("shiitake")
                            || text.contains("plant") || text.contains("nature")
                            || text.contains("food") || text.contains("organism")
                            || text.contains("forest") || text.contains("wood")
                            || text.contains("macro") || text.contains("ingredient")
                            || text.contains("vegetable") || text.contains("produce")
                            || text.contains("organic") || text.contains("flora")
                            || text.contains("botany") || text.contains("soil")
                            || text.contains("leaf") || text.contains("grass")
                            || text.contains("tree") || text.contains("branch")
                            || text.contains("ground") || text.contains("outdoor")
                            || text.contains("wild") || text.contains("natural")
                            || text.contains("biology") || text.contains("specimen")
                            || text.contains("earth") || text.contains("spore")) {
                        isMushroomDetected = true;
                        break;
                    }
                }

                if (!isMushroomDetected) {
                    bitmap.recycle();
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.btnAnalyze.setEnabled(true);
                        new AlertDialog.Builder(this)
                                .setTitle("No Mushroom Detected")
                                .setMessage("This image does not appear to contain a mushroom.\n\nPlease upload a clear, close-up photo of a mushroom for accurate classification.")
                                .setPositiveButton("OK", null)
                                .show();
                    });
                    return;
                }

                // Step 2: Classify edible vs poisonous
                MushroomClassifier.PredictionResult result = classifier.classify(bitmap);
                bitmap.recycle();

                String label = (result.predictedIndex == 1) ? "POISONOUS" : "EDIBLE";
                String confidence = (int) (result.confidence * 100) + "%";
                String warning = "This app is a decision-support tool only. Never consume wild mushrooms based solely on this result.";
                String uriString = imageUri.toString();

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.btnAnalyze.setEnabled(true);

                    Intent intent = new Intent(ImageInputActivity.this, ResultActivity.class);
                    intent.putExtra("label", label);
                    intent.putExtra("confidence", confidence);
                    intent.putExtra("confidence_float", result.confidence);
                    intent.putExtra("warning", warning);
                    intent.putExtra("image_uri", uriString);
                    startActivity(intent);
                });

            } catch (IOException e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.btnAnalyze.setEnabled(true);
                    Toast.makeText(this, "Model error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.btnAnalyze.setEnabled(true);
                    Toast.makeText(this, "Inference failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void showPermissionDenied(String type) {
        new AlertDialog.Builder(this)
                .setTitle(type + " Permission Required")
                .setMessage(type + " permission is needed to use this feature. Please enable it in Settings.")
                .setPositiveButton("OK", null)
                .show();
    }
}
