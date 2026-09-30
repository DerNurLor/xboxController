# PhonePad Studio editor

This is a self-contained browser layout editor for designing PhonePad button layouts. It uses plain HTML, CSS, and JavaScript, so it can be served locally or embedded into a desktop companion later.

Run it from the repository root:

```bash
python3 -m http.server 8080 -d editor
```

Open `http://localhost:8080` in a browser.

The editor supports absolute-positioned buttons, corner and edge resizing, numeric X/Y/width/height editing, dynamic 5px snapping with guides, reverse and disabled button states, preview interactions, templates, and named LocalStorage layouts.
