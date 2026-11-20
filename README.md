Bharat AI-SoC Challenge 2026-27

This project is an offline AI-powered agricultural assistant designed to help farmers identify crop diseases and receive treatment recommendations directly on an Android smartphone.

The application works completely offline and does not require internet connectivity during use.

Problem:

Many farmers do not have immediate access to agricultural experts, especially in rural areas with limited internet access.

Delayed disease identification can lead to reduced crop yield and financial losses.

This project aims to provide fast disease detection and guidance directly on a mobile device.

Solution:

The user captures or selects an image of a crop leaf.

A TensorFlow Lite vision model analyzes the image and identifies the disease.

The detected disease is passed to Llama 3.2 running locally using ExecuTorch.

The system then generates treatment recommendations and displays them to the user.

All processing takes place on-device without cloud services.

Features:

Offline disease detection
Android application
TensorFlow Lite vision model
Llama 3.2 integration
ExecuTorch deployment
English recommendations
Hindi recommendations
On-device AI inference
No internet required

How It Works:

User selects or captures a crop leaf image.
The image is analyzed by the TensorFlow Lite model.
The disease is identified.
The result is passed to Llama 3.2.
Treatment recommendations are generated.
The output is displayed to the user.

Technologies Used:

Android Studio
Kotlin
TensorFlow Lite
MobileNet
Python
Google Colab
Llama 3.2 1B
ExecuTorch

Project Structure:

app/ : Android source code
assets/ : Model and labels files
training/ : Model training notebooks
docs/ : Technical documentation

Future Improvements:

Pest detection
Disease severity estimation
Additional crop support
More Indian language support
Voice-based farmer assistance

Note:

The Llama model file is not included in this repository because of GitHub file size limitations.

Team NIELIT Ropar

Bharat AI-SoC Challenge 2026-27
