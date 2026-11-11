# Angelfish for Nintendo Switch

This preview is native homebrew for the original Nintendo Switch running Atmosphère. It uses a controller-first interface with YouTube Home, Search, playback, history and favourites.

## Try it on a Switch

1. Download `Angelfish.nro` from the **Angelfish-Switch** artifact on the [Build Switch Homebrew workflow](https://github.com/ShoalApps/angelfish/actions/workflows/build-switch.yml).
2. Copy it to `sdmc:/switch/Angelfish/Angelfish.nro`.
3. Hold **R** while opening an installed game to enter the Homebrew Menu in application mode. Keep holding **R** until the menu appears, then launch Angelfish.
4. In **Search**, press **X** to enter a video title. Select a result and press **A**. In the player, **A** pauses and **B** goes back.

For the first test, check that Home loads, search returns results, and a regular video plays with sound. Angelfish writes its diagnostic log to `sdmc:/switch/angelfish_switch.log`.

## Build from source

Install Docker, CMake and a host C++ compiler, then run from the Angelfish repository root:

```sh
bash switch/prepare.sh /tmp/angelfish-switch-source
cd /tmp/angelfish-switch-source
make -j2 host test-ump
./build.sh
```

The build produces `cmake-build-switch/angelfish_switch.nro`.

The native code is based on [Switch-NewPipe by mirusu400 and contributors](https://github.com/mirusu400/switch-newpipe/tree/a54b009f6d046a8ea7585e86c3c7ed41c4a57984) under GPL-3.0. `prepare.sh` fetches the pinned source, including its license, and applies the Angelfish changes in `angelfish.patch`. The bundled [Google Trust Services roots](https://pki.goog/repository/) are used by the native player to verify YouTube's streaming connections.
