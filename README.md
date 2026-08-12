![Wordmark.png](.github/assets/Wordmark.png)

# Kalia

An abstracted rendering engine implementation for Minecraft, allowing you to use Vulkan on 1.8.9.

## Download

We have no published releases of the mod right now. The only way you can get an artifact is via the experimental CI builds.

[Click here to download the latest nightly](https://nightly.link/LilithTechnologies/Kalia/workflows/build-commit/master/kalia-artifacts-master.zip).

## Minimum Graphical Requirements
Vulkan 1.2 supported graphics card, should be anything in the last ten years. Vulkan requires dynamic rendering. If multi draw is supported, it is utilised. Otherwise, indirect rendering is used.

## Architecture

Kalia is split into 2 parts:
- the rendering engine; and
- the mod / implementation

The engine API is designed to be low level, and agnostic of API or game. This engine can be used anywhere, not just in Minecraft.

In this mod, a Minecraft implementation has been created. To accomplish this, a small FFP emulation layer was written. This works well for most of MC and the mod offers performance better than vanilla, especially on macOS devices.

Additionally, we've ported Sodium to utilise this engine as well, as it is also designed to be agnostic of a specific rendering backend.

## Rendering API

We have a Kotlin/Java supported rendering API planned (a higher-level one, similar to Blaze3D), to allow developers to easily migrate their code to something Kalia-native, and if they are not migrating, to make it easy to write something Kalia-native.

## Future Plans & Roadmap

This is our roadmap for the foreseeable future. It may be altered at any point in time, depending on what happens with Minecraft. We'd like to stay consistent with how modern Minecraft goes, as well as how Sodium goes.

- [x] Initial Vulkan rendering backend
- [x] Port Sodium to run on Kalia
- [x] OpenGL 4.1 rendering backend
- [x] Instanced world rendering (entities, particles, nametags, etc.)
- [x] Rewrite In-Game HUD
- [ ] Stabilise mod
- [ ] Iris Shaders, on OpenGL _and_ Vulkan, and any future backends

## Credits

### Kalia

This mod is developed solely by [myself](https://github.com/ohlunasa) and [Cody](http://github.com/notdevcody). After numerous days of discussion, this mod was chosen to be licensed under All-Rights-Reserved. If you would like to know why, feel free to reach out to us. Grab a ☕ and let's talk.

### Terrain Renderer

This module of Kalia is licensed under LGPL-3.0.

I'd like to thank [@rhysdh540](https://github.com/rhysdh540/) for creating [Argentum](https://github.com/rhysdh540/), a performant 1.8.9 implementation of Celeritas. It powers the terrain rendering of Kalia.
And of course, embeddedt, for creating [Celeritas](https://git.taumc.org/embeddedt/celeritas/).