# VO2 Coach ⌚

An interval-training coach for your smartwatch that helps you improve your VO2 max.
It times every interval, tracks your heart rate, and tells you (bluntly) when to push harder.

---

## ✅ Supported watches

| Watch | Works? |
|---|---|
| Samsung Galaxy Watch4, 5, 6, 7, Ultra and newer | ✅ Yes (tested on Galaxy Watch7) |
| Google Pixel Watch (all) | ✅ Should work |
| Other **Wear OS 3 or newer** watches with a heart-rate sensor | ✅ Should work |
| Galaxy Watch3 or older (Tizen), Apple Watch, Fitbit, Garmin | ❌ No |

**Not sure?** On your watch go to **Settings → About watch → Software**. If it says **Wear OS** (version 3 or higher), you're good.

---

## 📋 What you need

- A supported watch (see above)
- A Windows, Mac or Linux computer
- The watch and computer on the **same Wi-Fi network**
- About 10 minutes

---

## 🚀 Install

There are two ways to install it. **Option A is the easiest.**

### Option A: Install the ready-made app (no coding)

**1. Download two things to your computer**
- The app: `app-debug.apk` from this repo's **[Releases](../../releases)** page
- Google's **Platform Tools**: https://developer.android.com/tools/releases/platform-tools
  (unzip it anywhere, for example your Desktop)

**2. Turn on developer mode on the watch**
1. Open **Settings → About watch → Software**.
2. Tap **Software version** 5 times, until it says *Developer mode turned on*.
3. Go back to **Settings → Developer options**.
4. Turn on **ADB debugging** and then **Wireless debugging**.

**3. Pair the watch with your computer**
1. On the watch, tap **Wireless debugging → Pair new device**.
   It shows a **6-digit code** and an **IP address:port**.
2. On the computer, open a terminal **inside the unzipped `platform-tools` folder**:
   - **Windows:** open the folder, click the address bar, type `powershell`, and press Enter.
   - **Mac / Linux:** right-click the folder and choose *Open in Terminal*.
3. Type the following, using the numbers shown on your watch:
   ```
   ./adb pair IP:PORT CODE
   ```
   Example: `./adb pair 192.168.1.23:41235 123456`

**4. Connect and install**
1. On the watch, go back one screen to the main **Wireless debugging** page.
   It shows another **IP address:port**. This port is different from the one used for pairing.
2. On the computer:
   ```
   ./adb connect IP:PORT
   ./adb install -r path/to/app-debug.apk
   ```
   💡 Tip: type `./adb install -r ` (with a space at the end), then drag the APK file into the terminal window to fill in its path.

When it says **Success**, you're done. 🎉

**5. Clean up (recommended)**
On the watch, turn **Wireless debugging** off again (Settings → Developer options).
The app stays installed.

### Option B: Build it yourself (for developers)

1. Install **[Android Studio](https://developer.android.com/studio)**.
2. Clone this repo and open the folder in Android Studio. Wait for *Gradle sync* to finish.
3. Do steps **2 and 3** from Option A above.
4. Pick your watch from the device dropdown at the top and press **Run ▶**.

---

## 🏃 First run

1. Open **VO2 Coach** from the watch's app list.
2. **Allow** body sensors and notifications. These are needed for heart rate.
   Location and physical activity are optional; they enable pace and distance.
3. Go to **Settings → Max heart rate** and set yours.
   (A rough guess is *220 − your age*. A measured max is better.)
4. Pick **Base** and press ▶. Start easy: harder modes unlock as you progress.

**Optional:** add the **VO2 Coach tile** (long-press the watch face → *Add tile*) for one-tap start.

---

## ❓ Problems?

| Problem | Fix |
|---|---|
| `adb` is not recognized | Make sure the terminal is open **inside** the `platform-tools` folder and you typed `./adb`. |
| Pairing fails or times out | The code expires quickly. Tap *Pair new device* again and use the new code. Make sure both devices are on the same Wi-Fi network (not a guest network). |
| `adb connect` fails | Use the port from the **main** Wireless debugging screen, not the pairing port. |
| No heart rate in the app | Wear the watch snugly and check you allowed the **Body sensors** permission. |
| Update to a newer version | Install the new APK the same way. Your history is kept. |
| Remove the app | Long-press its icon on the watch and choose **Uninstall**. ⚠️ This deletes your workout history. |

---

⚠️ **Health note:** This app gives training guidance, not medical advice. If you have a heart condition or feel unwell, stop and talk to a doctor.

👩‍💻 **Developers:** the full feature list, architecture and permissions are documented in **[DEVELOPER.md](DEVELOPER.md)**.
