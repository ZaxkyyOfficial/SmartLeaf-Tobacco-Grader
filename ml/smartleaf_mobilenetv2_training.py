#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SmartLeaf Tobacco Grader — MobileNetV2 Transfer Learning Training Script
=========================================================================

Sprint Reference : June 6, 2026
Architecture     : MobileNetV2 (ImageNet pre-trained, base frozen)
Classifier Head  : GlobalAveragePooling2D → Dropout(0.2) → Dense(4, softmax)
Classes          : [Immature, Pseudomature, Mature, Hypermature]
Hyperparameters  : lr=0.001 (Adam), batch_size=32, epochs=50
Input Shape      : (224, 224, 3) — RGB

Usage:
    python smartleaf_mobilenetv2_training.py \
        --data_dir ./dataset \
        --output_dir ./output

Expected dataset structure (ImageDataGenerator flow_from_directory):
    dataset/
    ├── train/
    │   ├── Immature/
    │   ├── Pseudomature/
    │   ├── Mature/
    │   └── Hypermature/
    └── validation/
        ├── Immature/
        ├── Pseudomature/
        ├── Mature/
        └── Hypermature/

Author  : SmartLeaf PKM-KC 2026 Team
License : Internal / Academic Use
"""

import os
import json
import argparse
import datetime
from pathlib import Path

import numpy as np
import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers, Model
from tensorflow.keras.applications import MobileNetV2
from tensorflow.keras.preprocessing.image import ImageDataGenerator
from tensorflow.keras.optimizers import Adam
from tensorflow.keras.callbacks import (
    ModelCheckpoint,
    EarlyStopping,
    ReduceLROnPlateau,
    TensorBoard,
    CSVLogger,
)

# ══════════════════════════════════════════════════════════════════════
# 1. CONSTANTS & HYPERPARAMETERS (Sprint Specification — June 6, 2026)
# ══════════════════════════════════════════════════════════════════════

# Target classes — must match labels.txt in Android app assets
CLASS_NAMES = ["Immature", "Pseudomature", "Mature", "Hypermature"]
NUM_CLASSES = len(CLASS_NAMES)  # 4

# Model input dimensions (MobileNetV2 default)
IMG_HEIGHT = 224
IMG_WIDTH = 224
IMG_CHANNELS = 3
INPUT_SHAPE = (IMG_HEIGHT, IMG_WIDTH, IMG_CHANNELS)

# Hyperparameters (exactly as defined in Sprint log)
LEARNING_RATE = 0.001
BATCH_SIZE = 32
EPOCHS = 50

# Regularization
DROPOUT_RATE = 0.2

# Reproducibility
RANDOM_SEED = 42


# ══════════════════════════════════════════════════════════════════════
# 2. MODEL ARCHITECTURE
# ══════════════════════════════════════════════════════════════════════

def build_model() -> Model:
    """
    Builds the SmartLeaf MobileNetV2 transfer learning model.

    Architecture:
        1. MobileNetV2 base (ImageNet weights, top removed, FROZEN)
        2. GlobalAveragePooling2D — reduces spatial dims to 1280-d vector
        3. Dropout(0.2) — regularization to prevent overfitting
        4. Dense(4, activation='softmax') — 4-class classifier head

    Returns:
        A compiled tf.keras.Model ready for training.
    """

    # ── Step 1: Load MobileNetV2 base (pre-trained on ImageNet) ──────
    base_model = MobileNetV2(
        weights="imagenet",      # Pre-trained ImageNet weights
        include_top=False,       # Remove the original 1000-class head
        input_shape=INPUT_SHAPE, # (224, 224, 3)
    )

    # ── Step 2: Freeze ALL base layers (transfer learning) ───────────
    # This prevents the pre-trained feature extraction layers from being
    # updated during training. Only the custom classifier head will learn.
    base_model.trainable = False

    print(f"[INFO] MobileNetV2 base loaded.")
    print(f"       Total base layers : {len(base_model.layers)}")
    print(f"       Trainable weights : {len(base_model.trainable_weights)}")
    print(f"       Non-trainable     : {len(base_model.non_trainable_weights)}")

    # ── Step 3: Build custom classifier head ─────────────────────────
    inputs = keras.Input(shape=INPUT_SHAPE, name="input_image")

    # MobileNetV2 includes its own preprocessing (scales pixels to [-1, 1])
    # but we apply it explicitly for clarity and portability
    x = tf.keras.applications.mobilenet_v2.preprocess_input(inputs)

    # Pass through frozen base
    x = base_model(x, training=False)  # training=False keeps BN in inference mode

    # Global Average Pooling: (batch, 7, 7, 1280) → (batch, 1280)
    x = layers.GlobalAveragePooling2D(name="global_avg_pool")(x)

    # Dropout for regularization
    x = layers.Dropout(DROPOUT_RATE, name="dropout")(x)

    # Final classification layer — 4 classes with softmax
    outputs = layers.Dense(
        NUM_CLASSES,
        activation="softmax",
        name="classifier_output",
    )(x)

    # ── Step 4: Assemble and compile ─────────────────────────────────
    model = Model(inputs=inputs, outputs=outputs, name="SmartLeaf_MobileNetV2")

    model.compile(
        optimizer=Adam(learning_rate=LEARNING_RATE),
        loss="categorical_crossentropy",
        metrics=[
            "accuracy",
            keras.metrics.Precision(name="precision"),
            keras.metrics.Recall(name="recall"),
        ],
    )

    model.summary()

    return model


# ══════════════════════════════════════════════════════════════════════
# 3. DATA PIPELINE
# ══════════════════════════════════════════════════════════════════════

def create_data_generators(data_dir: str):
    """
    Creates training and validation data generators using
    Keras ImageDataGenerator with standard augmentation for
    agricultural image classification.

    The generators will:
      - Rescale pixel values to [0, 255] (preprocessing is handled
        by mobilenet_v2.preprocess_input inside the model)
      - Apply augmentation to the training set only
      - Resize all images to (224, 224)
      - Use categorical (one-hot) labels for 4 classes

    Args:
        data_dir: Root dataset directory containing 'train/' and
                  'validation/' subdirectories.

    Returns:
        Tuple of (train_generator, val_generator).
    """
    train_dir = os.path.join(data_dir, "train")
    val_dir = os.path.join(data_dir, "validation")

    if not os.path.isdir(train_dir):
        raise FileNotFoundError(
            f"Training directory not found: {train_dir}\n"
            f"Expected structure: {data_dir}/train/<class_name>/"
        )
    if not os.path.isdir(val_dir):
        raise FileNotFoundError(
            f"Validation directory not found: {val_dir}\n"
            f"Expected structure: {data_dir}/validation/<class_name>/"
        )

    # Training data: augmented
    train_datagen = ImageDataGenerator(
        rescale=1.0,                # Keep pixel values as-is (model handles preprocessing)
        rotation_range=25,          # Slight rotation for leaf orientation variance
        width_shift_range=0.15,     # Horizontal shift
        height_shift_range=0.15,    # Vertical shift
        shear_range=0.1,            # Shear transformation
        zoom_range=0.2,             # Random zoom
        horizontal_flip=True,       # Leaves can appear mirrored
        vertical_flip=False,        # Vertical flip not realistic for leaf photos
        brightness_range=(0.8, 1.2),# Simulate field lighting variation
        fill_mode="nearest",        # Fill strategy for augmented edges
    )

    # Validation data: no augmentation, only rescaling
    val_datagen = ImageDataGenerator(
        rescale=1.0,
    )

    train_generator = train_datagen.flow_from_directory(
        train_dir,
        target_size=(IMG_HEIGHT, IMG_WIDTH),
        batch_size=BATCH_SIZE,
        class_mode="categorical",
        classes=CLASS_NAMES,        # Enforce consistent class ordering
        shuffle=True,
        seed=RANDOM_SEED,
        interpolation="bilinear",
    )

    val_generator = val_datagen.flow_from_directory(
        val_dir,
        target_size=(IMG_HEIGHT, IMG_WIDTH),
        batch_size=BATCH_SIZE,
        class_mode="categorical",
        classes=CLASS_NAMES,        # Must match training order
        shuffle=False,
        interpolation="bilinear",
    )

    # Validate class count
    assert train_generator.num_classes == NUM_CLASSES, (
        f"Expected {NUM_CLASSES} classes, found {train_generator.num_classes}: "
        f"{list(train_generator.class_indices.keys())}"
    )

    print(f"\n[INFO] Dataset loaded.")
    print(f"       Training samples   : {train_generator.samples}")
    print(f"       Validation samples  : {val_generator.samples}")
    print(f"       Classes             : {list(train_generator.class_indices.keys())}")
    print(f"       Class indices       : {train_generator.class_indices}")

    return train_generator, val_generator


# ══════════════════════════════════════════════════════════════════════
# 4. TRAINING CALLBACKS
# ══════════════════════════════════════════════════════════════════════

def create_callbacks(output_dir: str):
    """
    Creates a comprehensive set of training callbacks.

    Callbacks:
      - ModelCheckpoint: Saves best model (by val_accuracy)
      - EarlyStopping: Stops training if val_loss doesn't improve for 10 epochs
      - ReduceLROnPlateau: Halves LR if val_loss plateaus for 5 epochs
      - TensorBoard: Logs training metrics for visualization
      - CSVLogger: Logs epoch-level metrics to CSV for analysis

    Args:
        output_dir: Directory for saving checkpoints, logs, and exports.

    Returns:
        List of Keras Callback instances.
    """
    os.makedirs(output_dir, exist_ok=True)
    log_dir = os.path.join(output_dir, "logs", "tensorboard")
    os.makedirs(log_dir, exist_ok=True)

    callbacks = [
        # Save best model based on validation accuracy
        ModelCheckpoint(
            filepath=os.path.join(output_dir, "best_model.keras"),
            monitor="val_accuracy",
            mode="max",
            save_best_only=True,
            verbose=1,
        ),
        # Stop early if no improvement for 10 consecutive epochs
        EarlyStopping(
            monitor="val_loss",
            patience=10,
            restore_best_weights=True,
            verbose=1,
        ),
        # Reduce learning rate on plateau
        ReduceLROnPlateau(
            monitor="val_loss",
            factor=0.5,
            patience=5,
            min_lr=1e-6,
            verbose=1,
        ),
        # TensorBoard logging
        TensorBoard(
            log_dir=log_dir,
            histogram_freq=1,
            write_graph=True,
        ),
        # CSV log for easy plotting
        CSVLogger(
            filename=os.path.join(output_dir, "training_log.csv"),
            separator=",",
            append=False,
        ),
    ]

    return callbacks


# ══════════════════════════════════════════════════════════════════════
# 5. MODEL EVALUATION
# ══════════════════════════════════════════════════════════════════════

def evaluate_model(model: Model, val_generator, output_dir: str):
    """
    Evaluates the trained model on the validation set and prints
    per-class metrics.

    Args:
        model: Trained Keras model.
        val_generator: Validation data generator.
        output_dir: Directory for saving evaluation results.
    """
    print("\n" + "=" * 60)
    print("MODEL EVALUATION")
    print("=" * 60)

    # Overall metrics
    results = model.evaluate(val_generator, verbose=1)
    metric_names = model.metrics_names

    print(f"\n{'Metric':<20} {'Value':>10}")
    print("-" * 32)
    for name, value in zip(metric_names, results):
        print(f"{name:<20} {value:>10.4f}")

    # Per-class predictions for confusion matrix
    val_generator.reset()
    predictions = model.predict(val_generator, verbose=1)
    predicted_classes = np.argmax(predictions, axis=1)
    true_classes = val_generator.classes[:len(predicted_classes)]

    # Per-class accuracy
    print(f"\n{'Class':<20} {'Accuracy':>10}")
    print("-" * 32)
    for i, class_name in enumerate(CLASS_NAMES):
        mask = true_classes == i
        if mask.sum() > 0:
            class_acc = (predicted_classes[mask] == i).mean()
            print(f"{class_name:<20} {class_acc:>10.4f}")
        else:
            print(f"{class_name:<20} {'N/A':>10}")

    # Save evaluation summary
    eval_summary = {
        "timestamp": datetime.datetime.now().isoformat(),
        "metrics": dict(zip(metric_names, [float(v) for v in results])),
        "total_val_samples": int(val_generator.samples),
        "class_names": CLASS_NAMES,
    }
    eval_path = os.path.join(output_dir, "evaluation_summary.json")
    with open(eval_path, "w") as f:
        json.dump(eval_summary, f, indent=2)
    print(f"\n[INFO] Evaluation summary saved to: {eval_path}")


# ══════════════════════════════════════════════════════════════════════
# 6. TFLITE EXPORT
# ══════════════════════════════════════════════════════════════════════

def export_tflite(model: Model, output_dir: str):
    """
    Converts the trained Keras model to TensorFlow Lite format
    for on-device inference in the SmartLeaf Android app.

    Two versions are exported:
      1. Float32 (full precision) — for accuracy benchmarking
      2. Float16 quantized — for production deployment (smaller, faster)

    Args:
        model: Trained Keras model.
        output_dir: Directory for saving TFLite models.
    """
    print("\n" + "=" * 60)
    print("TFLITE EXPORT")
    print("=" * 60)

    tflite_dir = os.path.join(output_dir, "tflite")
    os.makedirs(tflite_dir, exist_ok=True)

    # ── Float32 (full precision) ─────────────────────────────────────
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    tflite_model_fp32 = converter.convert()

    fp32_path = os.path.join(tflite_dir, "model_float32.tflite")
    with open(fp32_path, "wb") as f:
        f.write(tflite_model_fp32)
    fp32_size_mb = os.path.getsize(fp32_path) / (1024 * 1024)
    print(f"[INFO] Float32 TFLite model saved: {fp32_path} ({fp32_size_mb:.2f} MB)")

    # ── Float16 quantized (recommended for mobile) ───────────────────
    converter_fp16 = tf.lite.TFLiteConverter.from_keras_model(model)
    converter_fp16.optimizations = [tf.lite.Optimize.DEFAULT]
    converter_fp16.target_spec.supported_types = [tf.float16]
    tflite_model_fp16 = converter_fp16.convert()

    fp16_path = os.path.join(tflite_dir, "model_float16.tflite")
    with open(fp16_path, "wb") as f:
        f.write(tflite_model_fp16)
    fp16_size_mb = os.path.getsize(fp16_path) / (1024 * 1024)
    print(f"[INFO] Float16 TFLite model saved: {fp16_path} ({fp16_size_mb:.2f} MB)")

    # ── Save labels.txt alongside the models ─────────────────────────
    labels_path = os.path.join(tflite_dir, "labels.txt")
    with open(labels_path, "w") as f:
        for class_name in CLASS_NAMES:
            f.write(f"{class_name}\n")
    print(f"[INFO] Labels file saved: {labels_path}")

    # ── Verify TFLite model I/O shape ────────────────────────────────
    interpreter = tf.lite.Interpreter(model_path=fp32_path)
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    print(f"\n[VERIFY] TFLite Model I/O:")
    print(f"  Input  shape : {input_details[0]['shape']}  dtype: {input_details[0]['dtype']}")
    print(f"  Output shape : {output_details[0]['shape']}  dtype: {output_details[0]['dtype']}")

    expected_output = [1, NUM_CLASSES]
    actual_output = list(output_details[0]["shape"])
    assert actual_output == expected_output, (
        f"Output shape mismatch! Expected {expected_output}, got {actual_output}"
    )
    print(f"  ✅ Output shape matches {NUM_CLASSES} classes.")


# ══════════════════════════════════════════════════════════════════════
# 7. TRAINING METADATA LOGGER
# ══════════════════════════════════════════════════════════════════════

def save_training_metadata(output_dir: str, history):
    """
    Saves a JSON file documenting the exact training configuration
    for reproducibility and audit compliance.
    """
    metadata = {
        "project": "SmartLeaf Tobacco Grader",
        "sprint_reference": "June 6, 2026 — CNN Architecture Design",
        "timestamp": datetime.datetime.now().isoformat(),
        "architecture": {
            "base_model": "MobileNetV2",
            "base_weights": "imagenet",
            "include_top": False,
            "base_trainable": False,
            "input_shape": list(INPUT_SHAPE),
            "classifier_head": [
                "GlobalAveragePooling2D",
                f"Dropout({DROPOUT_RATE})",
                f"Dense({NUM_CLASSES}, activation='softmax')",
            ],
        },
        "classes": CLASS_NAMES,
        "num_classes": NUM_CLASSES,
        "hyperparameters": {
            "optimizer": "Adam",
            "learning_rate": LEARNING_RATE,
            "loss": "categorical_crossentropy",
            "batch_size": BATCH_SIZE,
            "epochs_configured": EPOCHS,
            "epochs_completed": len(history.history["loss"]),
            "dropout_rate": DROPOUT_RATE,
        },
        "callbacks": [
            "ModelCheckpoint(monitor='val_accuracy', save_best_only=True)",
            "EarlyStopping(monitor='val_loss', patience=10)",
            "ReduceLROnPlateau(monitor='val_loss', factor=0.5, patience=5)",
            "TensorBoard",
            "CSVLogger",
        ],
        "final_metrics": {
            "train_loss": float(history.history["loss"][-1]),
            "train_accuracy": float(history.history["accuracy"][-1]),
            "val_loss": float(history.history["val_loss"][-1]),
            "val_accuracy": float(history.history["val_accuracy"][-1]),
        },
        "tensorflow_version": tf.__version__,
        "random_seed": RANDOM_SEED,
    }

    metadata_path = os.path.join(output_dir, "training_metadata.json")
    with open(metadata_path, "w") as f:
        json.dump(metadata, f, indent=2)
    print(f"\n[INFO] Training metadata saved to: {metadata_path}")


# ══════════════════════════════════════════════════════════════════════
# 8. MAIN ENTRY POINT
# ══════════════════════════════════════════════════════════════════════

def parse_args():
    parser = argparse.ArgumentParser(
        description="SmartLeaf MobileNetV2 Transfer Learning Training Script",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Example:
    python smartleaf_mobilenetv2_training.py \\
        --data_dir ./dataset \\
        --output_dir ./output
        """,
    )
    parser.add_argument(
        "--data_dir",
        type=str,
        required=True,
        help="Root dataset directory (must contain 'train/' and 'validation/' subdirs)",
    )
    parser.add_argument(
        "--output_dir",
        type=str,
        default="./output",
        help="Directory for model checkpoints, logs, and TFLite exports (default: ./output)",
    )
    return parser.parse_args()


def main():
    args = parse_args()

    # Set random seeds for reproducibility
    tf.random.set_seed(RANDOM_SEED)
    np.random.seed(RANDOM_SEED)

    print("=" * 60)
    print("  SmartLeaf Tobacco Grader — Model Training Pipeline")
    print("  Architecture : MobileNetV2 (Transfer Learning)")
    print(f"  Classes      : {CLASS_NAMES}")
    print(f"  LR={LEARNING_RATE}  Batch={BATCH_SIZE}  Epochs={EPOCHS}")
    print("=" * 60)

    # ── Build Model ──────────────────────────────────────────────────
    model = build_model()

    # ── Prepare Data ─────────────────────────────────────────────────
    train_gen, val_gen = create_data_generators(args.data_dir)

    # ── Create Callbacks ─────────────────────────────────────────────
    callbacks = create_callbacks(args.output_dir)

    # ── Train ────────────────────────────────────────────────────────
    print(f"\n[INFO] Starting training for {EPOCHS} epochs...")
    history = model.fit(
        train_gen,
        epochs=EPOCHS,
        validation_data=val_gen,
        callbacks=callbacks,
        verbose=1,
    )

    # ── Evaluate ─────────────────────────────────────────────────────
    evaluate_model(model, val_gen, args.output_dir)

    # ── Export to TFLite ─────────────────────────────────────────────
    export_tflite(model, args.output_dir)

    # ── Save Training Metadata ───────────────────────────────────────
    save_training_metadata(args.output_dir, history)

    print("\n" + "=" * 60)
    print("  ✅ TRAINING PIPELINE COMPLETE")
    print(f"  Model checkpoint : {args.output_dir}/best_model.keras")
    print(f"  TFLite (fp32)    : {args.output_dir}/tflite/model_float32.tflite")
    print(f"  TFLite (fp16)    : {args.output_dir}/tflite/model_float16.tflite")
    print(f"  Training log     : {args.output_dir}/training_log.csv")
    print(f"  Metadata         : {args.output_dir}/training_metadata.json")
    print("=" * 60)


if __name__ == "__main__":
    main()
