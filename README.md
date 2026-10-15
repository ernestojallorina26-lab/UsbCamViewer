# USB Cam Viewer

A simple Android app that shows the live picture from a USB camera / UVC capture card (like "USB Camera" apps), takes photos and records video. It works fully offline: the app has no internet permission and everything is saved on the phone.

## What you need

- An Android phone or tablet (8.0+) that supports USB OTG / USB host
- A USB OTG cable or adapter
- A UVC-compatible webcam, HDMI capture card, or similar USB video device

## Get the APK (no Android Studio needed)

1. Create a free account at github.com and make a new repository.
2. Upload the contents of this folder to it (keep the `.github` folder).
3. Open the repository's **Actions** tab, choose **Build APK**, press **Run workflow**.
4. When it finishes (about 5 minutes), open the run and download **UsbCamViewer-debug-apk**. Unzip it and copy `app-debug.apk` to your phone.
5. On the phone, allow "install unknown apps" for your file manager and install the APK.

## Or build in Android Studio

Open this folder in Android Studio (Hedgehog or newer), let it sync, then **Build > Build Bundle(s) / APK(s) > Build APK(s)**. The first sync needs internet to download the build libraries; after that, the app itself never needs internet.

## Using the app

1. Open the app and allow the camera and microphone permissions (Android requires the camera permission for USB cameras too; the microphone is only for video recording).
2. Plug in the camera with the OTG cable. Android will ask to let the app use the USB device; accept.
3. The live preview appears. Use **Photo** to take a picture and **Record / Stop** for video.

Photos and videos are saved in `Android/data/com.example.usbcamviewer/files/captures/` on the phone.

## Notes

- Not every phone supports USB cameras, and a few cameras use non-standard formats. If the preview stays black, try another camera or cable.
- The code uses the open-source AndroidUSBCamera library (`libausbc`). It was written without being compiled in the environment it was created in, so if the first build reports an error, send the message back and it can be fixed quickly.
