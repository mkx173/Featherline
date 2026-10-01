{ pkgs, config, ... }:

let
  emulatorApi = "36";
  emulatorAbi = if pkgs.stdenv.hostPlatform.isAarch64 then "arm64-v8a" else "x86_64";
  avdConfig = {
    "disk.dataPartition.size" = "8G";
    "hw.ramSize" = "3072";
    "hw.gpu.enabled" = "yes";
    "hw.gpu.mode" = "swiftshader";
  };
  installDebugApk = ''
    apk_dir="${config.devenv.root}/app/build/outputs/apk/play/debug"
    apk=$(jq -er '.elements[0].outputFile' "$apk_dir/output-metadata.json")
    adb install -r "$apk_dir/$apk"
  '';
in
{
  packages = [
    pkgs.git
    pkgs.jq
  ];

  languages.java = {
    enable = true;
    jdk.package = pkgs.jdk17;
    lsp.enable = false;
  };

  android = {
    enable = true;
    # Keep SDK 37 for builds; API 36 avoids the API 37 image's graphics crash.
    platforms.version = [
      emulatorApi
      "37.0"
    ];
    buildTools.version = [ "36.0.0" ];
    emulator.enable = true;
    systemImages.enable = true;
    systemImageTypes = [ "google_apis" ];
    abis = [ emulatorAbi ];
    ndk.enable = false;
    cmake.version = [ ];
    googleAPIs.enable = false;
    googleTVAddOns.enable = false;
    extras = [ ];
  };

  # Keep Gradle's downloaded wrapper and dependencies local to this checkout.
  env.GRADLE_USER_HOME = "${config.devenv.root}/.devenv/gradle";

  scripts.test-unit.exec = ''
    exec ./gradlew testPlayDebugUnitTest "$@"
  '';

  scripts.build-debug.exec = ''
    exec ./gradlew assemblePlayDebug "$@"
  '';

  tasks."android:debug-build" = {
    description = "Build the debug APK in app/build/outputs/apk/play/debug";
    cwd = config.devenv.root;
    exec = "./gradlew assemblePlayDebug";
  };

  # Set ANDROID_SERIAL when multiple devices are connected.
  tasks."android:debug-install" = {
    description = "Build and install the latest debug APK on the selected adb device";
    cwd = config.devenv.root;
    after = [ "android:debug-build" ];
    exec = ''
      set -euo pipefail
      ${installDebugApk}
    '';
  };

  # Run with: devenv tasks run android:debug-run
  # Set ANDROID_EMULATOR_PORT to use a port other than 5554.
  tasks."android:debug-run" = {
    description = "Build the latest debug APK, start an emulator, install and launch Featherline";
    cwd = config.devenv.root;
    after = [ "android:debug-build" ];
    exec = ''
      set -euo pipefail

      export ANDROID_USER_HOME="${config.devenv.root}/.android"
      export ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"
      mkdir -p "$ANDROID_AVD_HOME" "${config.devenv.root}/.devenv"

      emulator="$ANDROID_HOME/emulator/emulator"
      # The build tools bundle an older libc++; prefer the emulator's own copy.
      export LD_LIBRARY_PATH="$ANDROID_HOME/emulator/lib64''${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
      avd_name="featherline-api${emulatorApi}-${emulatorAbi}-8g"
      port="''${ANDROID_EMULATOR_PORT:-5554}"
      serial="emulator-$port"
      log="${config.devenv.root}/.devenv/emulator.log"

      if [[ ! "$port" =~ ^[0-9]+$ ]] || (( port < 5554 || port > 5682 || port % 2 != 0 )); then
        echo "ANDROID_EMULATOR_PORT must be an even port between 5554 and 5682." >&2
        exit 1
      fi

      avds=$("$emulator" -list-avds)
      if ! printf '%s\n' "$avds" | grep -Fxq "$avd_name"; then
        image_dir="$ANDROID_HOME/system-images/android-${emulatorApi}/google_apis/${emulatorAbi}"
        if [[ ! -f "$image_dir/package.xml" ]]; then
          echo "Expected an installed API ${emulatorApi} ${emulatorAbi} system image under $ANDROID_HOME/system-images." >&2
          exit 1
        fi
        "$ANDROID_HOME/cmdline-tools/${config.android.cmdLineTools.version}/bin/avdmanager" create avd \
          --name "$avd_name" --device pixel \
          --package "system-images;android-${emulatorApi};google_apis;${emulatorAbi}" <<< no
      fi

      # Apply the declared AVD settings before booting.
      avd_config="$ANDROID_AVD_HOME/$avd_name.avd/config.ini"
      ${pkgs.lib.concatStringsSep "\n" (
        pkgs.lib.mapAttrsToList (key: value: ''
          sed -i ${pkgs.lib.escapeShellArg "/^${pkgs.lib.escapeRegex key}=/d"} "$avd_config"
          printf '%s\n' ${pkgs.lib.escapeShellArg "${key}=${value}"} >> "$avd_config"
        '') avdConfig
      )}

      adb start-server
      emulator_pid=""
      if adb devices | awk '{print $1}' | grep -Fxq "$serial"; then
        running_avd=$(adb -s "$serial" emu avd name | tr -d '\r' | sed '/^OK$/d')
        if [[ "$running_avd" != "$avd_name" ]]; then
          echo "$serial is already in use. Set ANDROID_EMULATOR_PORT to another even port." >&2
          exit 1
        fi
        echo "Using $avd_name on $serial."
      else
        echo "Starting $avd_name on $serial (log: $log)."
        # Keep the emulator in its own session after the task returns.
        nohup ${pkgs.util-linux}/bin/setsid "$emulator" \
          -avd "$avd_name" -port "$port" -no-boot-anim -no-snapshot-load \
          > "$log" 2>&1 < /dev/null &
        emulator_pid=$!
      fi

      echo "Waiting for Android to finish booting..."
      deadline=$((SECONDS + 300))
      until [[ "$(${pkgs.coreutils}/bin/timeout 5 adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)" == 1 ]]; do
        if [[ -n "$emulator_pid" ]] && ! kill -0 "$emulator_pid" 2>/dev/null; then
          echo "Emulator exited before booting. See $log." >&2
          exit 1
        fi
        if (( SECONDS >= deadline )); then
          echo "Emulator boot timed out. See $log." >&2
          exit 1
        fi
        sleep 2
      done

      export ANDROID_SERIAL="$serial"
      ${installDebugApk}
      adb -s "$serial" shell am start -W \
        -n com.mkx.hrttracker.debug/com.mkx.hrttracker.MainActivity
    '';
  };

  tasks."android:e2e" = {
    description = "Start the project emulator and run end-to-end tests";
    cwd = config.devenv.root;
    after = [ "android:debug-run" ];
    exec = ''
      set -euo pipefail
      export ANDROID_SERIAL="emulator-''${ANDROID_EMULATOR_PORT:-5554}"
      exec ./gradlew connectedPlayDebugAndroidTest \
        -Pandroid.testInstrumentationRunnerArguments.package=com.mkx.hrttracker.e2e \
        --console=plain
    '';
  };
}
