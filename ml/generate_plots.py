"""
Kaydedilmis .keras modeli yukleyip Ingilizce grafikleri yeniden uretir.
"""
import os
os.environ["TF_ENABLE_ONEDNN_OPTS"] = "0"

import numpy as np
import matplotlib.pyplot as plt
import matplotlib
matplotlib.use("Agg")  # ekransiz calistirmak icin
import seaborn as sns
import tensorflow as tf
from tensorflow import keras
from sklearn.metrics import confusion_matrix, classification_report, roc_curve, auc
from pathlib import Path

IMG_SIZE   = (224, 224)
BATCH_SIZE = 32
DATA_DIR   = Path("dataset")
CLASS_NAMES = ["edible", "poisonous"]

test_ds = keras.utils.image_dataset_from_directory(
    DATA_DIR / "test",
    image_size=IMG_SIZE,
    batch_size=BATCH_SIZE,
    label_mode="binary",
    class_names=CLASS_NAMES,
    shuffle=False,
)

print("Model yukleniyor...")
model = keras.models.load_model("mushroom_model.keras")

print("Tahminler yapiliyor...")
y_prob = model.predict(test_ds).flatten()
y_pred = (y_prob > 0.5).astype(int)
y_true = np.concatenate([y for _, y in test_ds]).astype(int)


# ── Confusion Matrix ──────────────────────────────────────────────────────────
def plot_confusion_matrix(y_true, y_pred, save_path="confusion_matrix.png"):
    cm = confusion_matrix(y_true, y_pred)
    tn, fp, fn, tp = cm.ravel()

    fig = plt.figure(figsize=(16, 7))
    fig.suptitle("MycoVision - Confusion Matrix", fontsize=17,
                 fontweight="bold", y=1.0)

    ax      = fig.add_axes([0.03, 0.05, 0.44, 0.82])   # sol panel
    ax_heat = fig.add_axes([0.55, 0.10, 0.40, 0.75])   # sag panel

    BLUE = "#56B4E9"
    GRAY = "#D5D5D5"

    ax.set_xlim(-0.35, 2.1)
    ax.set_ylim(-0.1, 2.35)
    ax.set_aspect("equal")
    ax.axis("off")

    cells = [
        (0, 1, BLUE, "TP", "True Positives",  tp),
        (1, 1, GRAY, "FP", "False Positives", fp),
        (0, 0, GRAY, "FN", "False Negatives", fn),
        (1, 0, BLUE, "TN", "True Negatives",  tn),
    ]

    for (cx, cy, color, abbr, label, count) in cells:
        rect = plt.Rectangle((cx, cy), 1, 1, facecolor=color,
                              edgecolor="white", linewidth=3)
        ax.add_patch(rect)
        text_color = "white" if color == BLUE else "#444444"
        ax.text(cx + 0.5, cy + 0.68, abbr,
                ha="center", va="center", fontsize=28, fontweight="bold",
                color=text_color)
        ax.text(cx + 0.5, cy + 0.42, label,
                ha="center", va="center", fontsize=11, color=text_color)
        ax.text(cx + 0.5, cy + 0.18, f"n = {count}",
                ha="center", va="center", fontsize=13, fontweight="bold",
                color=text_color)

    # Ust eksen etiketi
    ax.text(1.0, 2.28, "Actual Class",
            ha="center", va="center", fontsize=13, fontweight="bold")
    ax.text(0.5, 2.14, "Poisonous (+)",
            ha="center", va="center", fontsize=11)
    ax.text(1.5, 2.14, "Edible (-)",
            ha="center", va="center", fontsize=11)

    # Sol eksen etiketi
    ax.text(-0.28, 1.0, "Predicted\nClass",
            ha="center", va="center", fontsize=13, fontweight="bold", rotation=90)
    ax.text(-0.14, 1.5, "Poisonous (+)",
            ha="center", va="center", fontsize=11, rotation=90)
    ax.text(-0.14, 0.5, "Edible (-)",
            ha="center", va="center", fontsize=11, rotation=90)

    ax.set_title("TP / FP / FN / TN Breakdown", fontsize=13, pad=6, loc="center", x=1.0)

    cm_norm = cm.astype(float) / cm.sum(axis=1, keepdims=True)
    annot = np.array([
        [f"TN\n{tn}\n({cm_norm[0,0]:.1%})", f"FP\n{fp}\n({cm_norm[0,1]:.1%})"],
        [f"FN\n{fn}\n({cm_norm[1,0]:.1%})", f"TP\n{tp}\n({cm_norm[1,1]:.1%})"],
    ])
    sns.heatmap(cm_norm, annot=annot, fmt="", cmap="Blues", ax=ax_heat,
                xticklabels=["Edible", "Poisonous"],
                yticklabels=["Edible", "Poisonous"],
                linewidths=2, linecolor="white",
                annot_kws={"size": 13, "weight": "bold"},
                vmin=0, vmax=1, cbar_kws={"label": "Rate"})
    ax_heat.set_title("Normalized Confusion Matrix", fontsize=13)
    ax_heat.set_ylabel("Actual Class",    fontsize=11)
    ax_heat.set_xlabel("Predicted Class", fontsize=11)

    plt.tight_layout()
    plt.savefig(save_path, dpi=150, bbox_inches="tight")
    print(f"Saved: {save_path}")

    print("\nClassification Report:")
    print(classification_report(y_true, y_pred,
                                target_names=["Edible", "Poisonous"]))


# ── ROC Curve ─────────────────────────────────────────────────────────────────
def plot_roc(y_true, y_prob, save_path="roc_curve.png"):
    fpr, tpr, _ = roc_curve(y_true, y_prob)
    roc_auc = auc(fpr, tpr)

    plt.figure(figsize=(7, 6))
    plt.plot(fpr, tpr, color="#2196F3", lw=2,
             label=f"ROC Curve (AUC = {roc_auc:.4f})")
    plt.plot([0, 1], [0, 1], color="gray", linestyle="--", lw=1,
             label="Random Classifier")
    plt.fill_between(fpr, tpr, alpha=0.1, color="#2196F3")
    plt.xlabel("False Positive Rate (FPR)", fontsize=12)
    plt.ylabel("True Positive Rate (TPR)", fontsize=12)
    plt.title("MycoVision - ROC Curve", fontsize=14, fontweight="bold")
    plt.legend(loc="lower right", fontsize=11)
    plt.grid(alpha=0.3)
    plt.tight_layout()
    plt.savefig(save_path, dpi=150, bbox_inches="tight")
    print(f"Saved: {save_path}")


plot_confusion_matrix(y_true, y_pred)
plot_roc(y_true, y_prob)
print("\nDone. Check confusion_matrix.png and roc_curve.png")
