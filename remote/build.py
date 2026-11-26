#!/usr/bin/env python3
"""Builds the stand-alone MobileDigger-Remote.html (script inlined) from extension/."""
import pathlib
here = pathlib.Path(__file__).parent
html = (here / "extension" / "remote.html").read_text()
js = (here / "extension" / "remote.js").read_text()
out = html.replace('<script src="remote.js"></script>', "<script>\n" + js + "\n</script>")
assert out != html, "script tag not found"
(here / "MobileDigger-Remote.html").write_text(out)
print("wrote MobileDigger-Remote.html")
