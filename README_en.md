# HyperSelect

**English** · [简体中文](README.md)

A Xiaomi HyperOS module to customize and clean up Text Selection, System Share, and Open With dialogs.

---

## Features

- **Remove AI Writing**: Completely eliminate the annoying AI writing bubble that always sits at the first spot when selecting text.
- **Text Selection Menu**: Freely drag to reorder actions, and swipe left to hide items you never use.
- **System Share Sheet**: Pin frequently used apps (like messaging or cloud drives) to the top, and swipe away cluttered share targets.
- **Open With Dialog**: Clean up duplicate and redundant entries, keeping your preferred media players and document viewers pinned first.
- **Instant Effect**: All changes take effect immediately via LSPosed hooks — no reboot or soft-reboot required.

---

## Preview

<p align="center">
  <img src="docs/preview_text_select.png" width="480" alt="Text selection comparison" />
</p>

<p align="center">
  <img src="docs/preview_menu_sort.png" width="560" alt="Menu sorting" />
</p>

<p align="center">
  <img src="docs/preview_share.png" width="380" alt="Share sheet pinning" />
</p>

---

## Environment

- **OS**: Xiaomi HyperOS 2.0 / OS 3 (theoretically 1.0+)
- **Framework**: LSPosed (based on LibXposed API 102)
- **Scope**: System Framework, IntentResolver

---

## License

[GNU General Public License v3.0](LICENSE)
