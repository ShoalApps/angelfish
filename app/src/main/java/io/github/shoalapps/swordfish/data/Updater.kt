package io.github.shoalapps.swordfish.data

import io.github.shoalapps.common.update.GitHubUpdater

/** Keeps Angelfish up to date from its GitHub Releases, with the shared Shoal library's updater. */
object Updater : GitHubUpdater("https://github.com/ShoalApps/angelfish", "Angelfish.apk")
