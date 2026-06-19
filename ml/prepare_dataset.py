"""
İki dataseti birleştirir ve train/val/test klasörlerine böler:
  1. daniilonishchenko/mushrooms-images-classification-215  (215 tür, etiketli)
  2. marcosvolpato/edible-and-poisonous-fungi               (direkt edible/poisonous)
"""

import shutil, random
from pathlib import Path

OUTPUT_DIR  = Path("dataset")
TRAIN_RATIO = 0.70
VAL_RATIO   = 0.15
SEED        = 42
EXTENSIONS  = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}

random.seed(SEED)

# ── Dataset 1: 215-tür, zehirli/yenilebilir etiketleri ───────────────────────
RAW1 = Path("D:/kagglehub_cache/datasets/daniilonishchenko/mushrooms-images-classification-215/versions/2/data/data")

POISONOUS_SPECIES = {
    "deathcap", "destroying_angel", "deadly_webcap", "funeral_bell",
    "fly_agaric", "panthercap", "devils_bolete", "false_morel",
    "deadly_fibrecap", "lilac_fibrecap", "white_fibrecap", "amanita_gemmata",
    "brown_rollrim", "sulphur_tuft", "the_sickener", "earthballs",
    "fools_funnel", "poison_pie", "common_rustgill", "false_chanterelle",
    "liberty_cap", "beechwood_sickener", "bitter_bolete", "bitter_beech_bolete",
    "magpie_inkcap", "yellow_stainer", "shaggy_scalycap", "golden_scalycap",
    "grey_knight", "stinking_dapperling", "freckled_dapperling", "blue_roundhead",
    "spectacular_rustgill", "egghead_mottlegill", "weeping_widow",
    "false_deathcap", "woolly_milkcap", "fleecy_milkcap", "false_saffron_milkcap",
    "bruising_webcap", "lurid_bolete", "rooting_bolete", "clouded_agaric",
    "golden_bootleg", "medusa_mushroom", "warted_amanita", "white_false_death_cap",
    "white_domecap", "peppery_bolete", "geranium_brittlegill", "pavement_mushroom",
    "yellow_false_truffle", "blackening_waxcap", "trooping_funnel",
    "plums_and_custard", "scarletina_bolete", "the_blusher", "honey_fungus",
    "elfin_saddle", "white_saddle",
}

images = {"edible": [], "poisonous": []}

for sp_dir in sorted(RAW1.iterdir()):
    if not sp_dir.is_dir():
        continue
    label = "poisonous" if sp_dir.name in POISONOUS_SPECIES else "edible"
    imgs  = [f for f in sp_dir.iterdir() if f.suffix.lower() in EXTENSIONS]
    images[label].extend(imgs)

print(f"Dataset 1 — Edible: {len(images['edible'])}, Poisonous: {len(images['poisonous'])}")

# ── Dataset 2: direkt edible/poisonous ────────────────────────────────────────
RAW2 = Path("D:/kagglehub_cache/datasets/marcosvolpato/edible-and-poisonous-fungi/versions/1")

for folder in RAW2.rglob("*"):
    if not folder.is_dir():
        continue
    name = folder.name.lower()
    if "edible" in name and "poisonous" not in name:
        label = "edible"
    elif "poisonous" in name:
        label = "poisonous"
    else:
        continue
    imgs = [f for f in folder.iterdir() if f.suffix.lower() in EXTENSIONS]
    images[label].extend(imgs)

print(f"Dataset 1+2 — Edible: {len(images['edible'])}, Poisonous: {len(images['poisonous'])}")
print(f"Toplam: {len(images['edible']) + len(images['poisonous'])} gorsel")

# ── Eski dataset klasörünü temizle ────────────────────────────────────────────
if OUTPUT_DIR.exists():
    shutil.rmtree(OUTPUT_DIR)
    print("Eski dataset/ temizlendi.")

# ── Train / Val / Test böl ve kopyala ────────────────────────────────────────
def split_and_copy(imgs, label):
    random.shuffle(imgs)
    n       = len(imgs)
    n_train = int(n * TRAIN_RATIO)
    n_val   = int(n * VAL_RATIO)
    splits  = {
        "train": imgs[:n_train],
        "val":   imgs[n_train:n_train + n_val],
        "test":  imgs[n_train + n_val:],
    }
    for split, files in splits.items():
        dest = OUTPUT_DIR / split / label
        dest.mkdir(parents=True, exist_ok=True)
        for i, src in enumerate(files):
            dst = dest / f"{label}_{i:05d}{src.suffix.lower()}"
            shutil.copy2(src, dst)
        print(f"  {split}/{label}: {len(files)}")

print("\nKopyalaniyor...")
split_and_copy(images["edible"],    "edible")
split_and_copy(images["poisonous"], "poisonous")

print("\n-- Hazir --")
total = 0
for split in ("train", "val", "test"):
    for label in ("edible", "poisonous"):
        d = OUTPUT_DIR / split / label
        c = len(list(d.glob("*"))) if d.exists() else 0
        total += c
        print(f"  {split}/{label}: {c}")
print(f"  TOPLAM: {total}")
