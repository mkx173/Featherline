{ pkgs, config, ... }:

{
  packages = [ pkgs.git ];

  languages.java = {
    enable = true;
    jdk.package = pkgs.jdk17;
    lsp.enable = false;
  };

  android = {
    enable = true;
    platforms.version = [ "37" ];
    buildTools.version = [ "36.0.0" ];
    emulator.enable = false;
    systemImages.enable = false;
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
}
