"""
MycoVision - Mantar Zehirliliği Sınıflandırma Modeli
MobileNetV2 transfer learning + TFLite export + Confusion Matrix

Dataset klasör yapısı:
  dataset/
  ├── train/
  │   ├── edible/       <- yenilebilir mantar görselleri
  │   └── poisonous/    <- zehirli mantar görselleri
  ├── val/
  │   ├── edible/
  │   └── poisonous/
  └── test/
      ├── edible/
      └── poisonous/

Önerilen dataset: Kaggle - "Mushroom Classification" image datasets
"""

import os
import numpy as np
import matplotlib.pyplot as plt
import matplotlib.gridspec as gridspec
import seaborn as sns
import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers
from sklearn.metrics import (
    confusion_matrix, classification_report,
    roc_curve, auc
)
from pathlib import Path

# ── Ayarlar ──────────────────────────────────────────────────────────────────
IMG_SIZE        = (224, 224)
BATCH_SIZE      = 32
INITIAL_EPOCHS  = 20
FINE_TUNE_EPOCHS = 10
DATA_DIR        = Path("dataset")
MODEL_PATH      = "mushroom_model.keras"
TFLITE_PATH     = "mushroom_model.tflite"
CLASS_NAMES     = ["edible", "poisonous"]   # 0=yenilebilir, 1=zehirli
SEED            = 42

tf.random.set_seed(SEED)
np.random.seed(SEED)


# ── Veri Yükleme ─────────────────────────────────────────────────────────────
def load_dataset(split: str):
    return keras.utils.image_dataset_from_directory(
        DATA_DIR / split,
        image_size=IMG_SIZE,
        batch_size=BATCH_SIZE,
        label_mode="binary",
        class_names=CLASS_NAMES,
        shuffle=(split == "train"),
        seed=SEED,
    )

print("Veri seti yükleniyor...")
train_ds = load_dataset("train")
val_ds   = load_dataset("val")
test_ds  = load_dataset("test")

AUTOTUNE = tf.data.AUTOTUNE
train_ds = train_ds.cache().prefetch(AUTOTUNE)
val_ds   = val_ds.cache().prefetch(AUTOTUNE)
test_ds  = test_ds.cache().prefetch(AUTOTUNE)

# ── Class Weighting ───────────────────────────────────────────────────────────
# Poisonous sınıfı az örnekli → modele daha ağır ceza uygula
n_edible    = sum(1 for _, y in train_ds.unbatch() if float(y) == 0.0)
n_poisonous = sum(1 for _, y in train_ds.unbatch() if float(y) == 1.0)
n_total     = n_edible + n_poisonous
CLASS_WEIGHT = {
    0: n_total / (2 * n_edible),       # edible   → ~0.70
    1: n_total / (2 * n_poisonous),    # poisonous → ~1.77
}
print(f"Class weights: edible={CLASS_WEIGHT[0]:.3f}, poisonous={CLASS_WEIGHT[1]:.3f}")


# ── Model ─────────────────────────────────────────────────────────────────────
data_augmentation = keras.Sequential([
    layers.RandomFlip("horizontal_and_vertical"),
    layers.RandomRotation(0.3),
    layers.RandomZoom(0.2),
    layers.RandomBrightness(0.15),
    layers.RandomContrast(0.15),
], name="augmentation")

base_model = keras.applications.MobileNetV2(
    input_shape=(*IMG_SIZE, 3),
    include_top=False,
    weights="imagenet",
)
base_model.trainable = False

inputs  = keras.Input(shape=(*IMG_SIZE, 3))
x       = data_augmentation(inputs)
x       = keras.applications.mobilenet_v2.preprocess_input(x)  # [-1, 1]
x       = base_model(x, training=False)
x       = layers.GlobalAveragePooling2D()(x)
x       = layers.BatchNormalization()(x)
x       = layers.Dropout(0.3)(x)
outputs = layers.Dense(1, activation="sigmoid")(x)

model = keras.Model(inputs, outputs, name="MycoVision")
model.summary()


# ── Aşama 1: Sadece üst katmanlar (Transfer learning) ────────────────────────────────────────────
model.compile(
    optimizer=keras.optimizers.Adam(1e-3),
    loss="binary_crossentropy",
    metrics=["accuracy", keras.metrics.Precision(name="precision"),
             keras.metrics.Recall(name="recall")],
)

callbacks = [
    keras.callbacks.EarlyStopping(patience=5, restore_best_weights=True,
                                   monitor="val_accuracy"),
    keras.callbacks.ReduceLROnPlateau(factor=0.3, patience=3,
                                       monitor="val_loss"),
    keras.callbacks.ModelCheckpoint(MODEL_PATH, save_best_only=True,
                                     monitor="val_accuracy"),
]

print("\n── Aşama 1: Transfer Learning ──")
history1 = model.fit(train_ds, validation_data=val_ds,
                     epochs=INITIAL_EPOCHS, callbacks=callbacks,
                     class_weight=CLASS_WEIGHT)


# ── Aşama 2: Fine-tuning ──────────────────────────────────────────────────────
base_model.trainable = True
for layer in base_model.layers[:-30]:   # son 30 katmanı aç
    layer.trainable = False

model.compile(
    optimizer=keras.optimizers.Adam(1e-5),
    loss="binary_crossentropy",
    metrics=["accuracy", keras.metrics.Precision(name="precision"),
             keras.metrics.Recall(name="recall")],
)

print("\n── Aşama 2: Fine-Tuning ──")
history2 = model.fit(train_ds, validation_data=val_ds,
                     epochs=FINE_TUNE_EPOCHS, callbacks=callbacks,
                     class_weight=CLASS_WEIGHT)

model = keras.models.load_model(MODEL_PATH)


# ── Değerlendirme ─────────────────────────────────────────────────────────────
print("\n-- Test Set Evaluation --")
test_loss, test_acc, test_prec, test_rec = model.evaluate(test_ds)
f1 = 2 * test_prec * test_rec / (test_prec + test_rec + 1e-8)
print(f"Accuracy  : {test_acc:.4f}")
print(f"Precision : {test_prec:.4f}")
print(f"Recall    : {test_rec:.4f}")
print(f"F1 Score  : {f1:.4f}")

y_prob  = model.predict(test_ds).flatten()
y_pred  = (y_prob > 0.5).astype(int)
y_true  = np.concatenate([y for _, y in test_ds]).astype(int)


# ── Confusion Matrix (TP/FP/FN/TN style) ─────────────────────────────────────
def plot_confusion_matrix(y_true, y_pred, save_path="confusion_matrix.png"):
    # y=1 → Poisonous (positive class)
    cm = confusion_matrix(y_true, y_pred)
    tn, fp, fn, tp = cm.ravel()
    total = cm.sum()

    fig, axes = plt.subplots(1, 2, figsize=(15, 6))
    fig.suptitle("MycoVision - Confusion Matrix", fontsize=17, fontweight="bold", y=1.02)

    # ── Sol: TP/FP/FN/TN tablosu ─────────────────────────────────────────────
    ax = axes[0]
    ax.set_xlim(0, 2)
    ax.set_ylim(0, 2)
    ax.set_aspect("equal")
    ax.axis("off")

    BLUE  = "#56B4E9"   # doğru tahminler
    GRAY  = "#D5D5D5"   # yanlış tahminler
    WHITE = "white"

    cells = [
        # (x, y, bg_color, big_text, sub_text, count)
        (0, 1, BLUE, "TP", "True Positives",  tp),
        (1, 1, GRAY, "FP", "False Positives", fp),
        (0, 0, GRAY, "FN", "False Negatives", fn),
        (1, 0, BLUE, "TN", "True Negatives",  tn),
    ]

    for (cx, cy, color, abbr, label, count) in cells:
        rect = plt.Rectangle((cx, cy), 1, 1, facecolor=color,
                              edgecolor="white", linewidth=3)
        ax.add_patch(rect)
        ax.text(cx + 0.5, cy + 0.68, abbr,
                ha="center", va="center", fontsize=26, fontweight="bold",
                color="white" if color == BLUE else "#444444")
        ax.text(cx + 0.5, cy + 0.42, label,
                ha="center", va="center", fontsize=11,
                color="white" if color == BLUE else "#444444")
        ax.text(cx + 0.5, cy + 0.18, f"n = {count}",
                ha="center", va="center", fontsize=13, fontweight="bold",
                color="white" if color == BLUE else "#444444")

    # Eksen etiketleri
    ax.text(1.0, 2.12, "Actual Class", ha="center", va="center",
            fontsize=13, fontweight="bold")
    ax.text(0.5, 2.06, "Poisonous (+)", ha="center", va="center", fontsize=11)
    ax.text(1.5, 2.06, "Edible (−)",   ha="center", va="center", fontsize=11)

    ax.text(-0.12, 1.0, "Predicted\nClass", ha="center", va="center",
            fontsize=13, fontweight="bold", rotation=90)
    ax.text(-0.06, 1.5, "Poisonous (+)", ha="center", va="center",
            fontsize=11, rotation=90)
    ax.text(-0.06, 0.5, "Edible (−)",   ha="center", va="center",
            fontsize=11, rotation=90)

    ax.set_title("TP / FP / FN / TN Breakdown", fontsize=13, pad=14)

    # ── Sağ: Normalize heatmap ────────────────────────────────────────────────
    cm_norm = cm.astype(float) / cm.sum(axis=1, keepdims=True)
    class_labels = ["Edible", "Poisonous"]

    annot = np.array([
        [f"TN\n{tn}\n({cm_norm[0,0]:.1%})", f"FP\n{fp}\n({cm_norm[0,1]:.1%})"],
        [f"FN\n{fn}\n({cm_norm[1,0]:.1%})", f"TP\n{tp}\n({cm_norm[1,1]:.1%})"],
    ])

    sns.heatmap(cm_norm, annot=annot, fmt="", cmap="Blues", ax=axes[1],
                xticklabels=class_labels, yticklabels=class_labels,
                linewidths=2, linecolor="white",
                annot_kws={"size": 13, "weight": "bold"},
                vmin=0, vmax=1, cbar_kws={"label": "Rate"})
    axes[1].set_title("Normalized Confusion Matrix", fontsize=13)
    axes[1].set_ylabel("Actual Class",    fontsize=11)
    axes[1].set_xlabel("Predicted Class", fontsize=11)

    plt.tight_layout()
    plt.savefig(save_path, dpi=150, bbox_inches="tight")
    print(f"Saved: {save_path}")
    plt.show()

    print("\nClassification Report:")
    print(classification_report(y_true, y_pred, target_names=["Edible", "Poisonous"]))


# ── ROC Eğrisi ────────────────────────────────────────────────────────────────
def plot_roc(y_true, y_prob, save_path="roc_curve.png"):
    fpr, tpr, _ = roc_curve(y_true, y_prob)
    roc_auc     = auc(fpr, tpr)

    plt.figure(figsize=(7, 6))
    plt.plot(fpr, tpr, color="#2196F3", lw=2,
             label=f"ROC Curve (AUC = {roc_auc:.4f})")
    plt.plot([0, 1], [0, 1], color="gray", linestyle="--", lw=1, label="Random Classifier")
    plt.fill_between(fpr, tpr, alpha=0.1, color="#2196F3")
    plt.xlabel("False Positive Rate (FPR)", fontsize=12)
    plt.ylabel("True Positive Rate (TPR)", fontsize=12)
    plt.title("MycoVision - ROC Curve", fontsize=14, fontweight="bold")
    plt.legend(loc="lower right", fontsize=11)
    plt.grid(alpha=0.3)
    plt.tight_layout()
    plt.savefig(save_path, dpi=150, bbox_inches="tight")
    print(f"Saved: {save_path}")
    plt.show()


# ── Eğitim Geçmişi ────────────────────────────────────────────────────────────
def plot_training_history(h1, h2, save_path="training_history.png"):
    acc  = h1.history["accuracy"]     + h2.history["accuracy"]
    val  = h1.history["val_accuracy"] + h2.history["val_accuracy"]
    loss = h1.history["loss"]         + h2.history["loss"]
    vloss= h1.history["val_loss"]     + h2.history["val_loss"]
    epochs = range(1, len(acc) + 1)
    ft_start = len(h1.history["accuracy"]) + 1

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 5))
    fig.suptitle("MycoVision - Training History", fontsize=15, fontweight="bold")

    for ax, train_vals, val_vals, metric in [
        (ax1, acc, val,   "Accuracy"),
        (ax2, loss, vloss, "Loss"),
    ]:
        ax.plot(epochs, train_vals, "b-o", markersize=4, label="Train")
        ax.plot(epochs, val_vals,   "r-o", markersize=4, label="Validation")
        ax.axvline(ft_start, color="green", linestyle="--", alpha=0.7,
                   label="Fine-tune start")
        ax.set_xlabel("Epoch")
        ax.set_title(metric)
        ax.legend()
        ax.grid(alpha=0.3)

    plt.tight_layout()
    plt.savefig(save_path, dpi=150, bbox_inches="tight")
    print(f"Saved: {save_path}")
    plt.show()


plot_confusion_matrix(y_true, y_pred)
plot_roc(y_true, y_prob)
plot_training_history(history1, history2)


# ── TFLite Export ─────────────────────────────────────────────────────────────
print("\n── TFLite Dönüştürme ──")
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]   # int8 dynamic-range quant


tflite_model = converter.convert()

with open(TFLITE_PATH, "wb") as f:
    f.write(tflite_model)

size_kb = len(tflite_model) / 1024
print(f"TFLite modeli kaydedildi: {TFLITE_PATH}  ({size_kb:.1f} KB)")
print("Bu dosyayı android/app/src/main/assets/ klasörüne kopyalayın.")

# labels.txt oluştur
with open("labels.txt", "w") as f:
    f.write("edible\npoisonous\n")
print("labels.txt oluşturuldu.")
