# gitpatcher
gitpatcher is a Gradle plugin that can manage patches for Git repositories for you ([example]). This is useful if you need a few smaller changes for a Git repository that can't be contributed upstream, but you still easily want to keep up to date with upstream.
gitpatcher manages a local submodule as base, and applies patches from a configurable folder in an extra repository. A local Git installation on 
the PATH is required for it to run.

# Installation
1. Add a submodule for the project you want to patch.
2. Apply gitpatcher to your Gradle project:

  ```gradle
  plugins {
      id 'ca.stellardrift.gitpatcher' version '1.1.0'
  }
  ```
3. Configure gitpatcher:

  ```gradle
  gitPatcher.patchedRepos {
      // 'repo' is arbitrary; it will be used for task names (see below section)
      'repo' {
          // The submodule path you just created
          submodule = 'upstream'
          // The target folder for the patched repositories
          target = file('target')
          // The folder where the patches are saved
          patches = file('patches')
      }
  }
  ```
4. That's it! Now you can initialize your repository (see below) and start making commits to it. Then just make the patches and you can apply it to the target repository as often as you want.

# Tasks
| Name                                | Description                                                                          |
|-------------------------------------|--------------------------------------------------------------------------------------|
| `update[CapitalizedName]Submodules` | Initializes the submodule and updates it if it is outdated.                          |
| `apply[CapitalizedName]Patches`     | Initializes the target repository and applies the patches from the patch folder.     |
| `make[CapitalizedName]Patches`      | Creates or updates the patches in the patch folder.                                  |
| `updateSubmodules`                  | Lifecycle task which depends on all other `update[CapitalizedName]Submodules` tasks. |
| `applyPatches`                      | Lifecycle task which depends on all other `apply[CapitalizedName]Patches` tasks.     |
| `makePatches`                       | Lifecycle task which depends on all other `make[CapitalizedName]Patches` tasks.      |

[example]: https://github.com/LapisBlue/Pore/tree/master/patches

# History

gitpatcher was originally produced by Minecrell and the Cadix team. The MinecraftForge team picked up development for a period of a few years.
This project is now maintained by Stellardrift.

gitpatcher is released under [the MIT license](./LICENSE)
