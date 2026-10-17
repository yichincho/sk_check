"""Validate apkanalyzer's *packaged* release manifest, not the source manifest."""
import pathlib
import sys
import xml.etree.ElementTree as ET

manifest = ET.parse(sys.argv[1]).getroot()
android = "{http://schemas.android.com/apk/res/android}"
package = "com.yichincho.skcheck"
assert manifest.get("package") == package, "applicationId changed"
app = manifest.find("application")
assert app is not None
assert app.get(android + "enabled", "true") == "true"
assert app.get(android + "icon"), "Missing icon"
assert app.get(android + "roundIcon"), "Missing roundIcon"
assert app.get(android + "label"), "Missing app label"
launchers = []
for activity in app.findall("activity"):
    for intent in activity.findall("intent-filter"):
        actions = {a.get(android + "name") for a in intent.findall("action")}
        categories = {c.get(android + "name") for c in intent.findall("category")}
        if "android.intent.action.MAIN" in actions and "android.intent.category.LAUNCHER" in categories:
            assert activity.get(android + "exported") == "true"
            assert activity.get(android + "enabled", "true") == "true"
            launchers.append(activity.get(android + "name"))
assert launchers == [package + ".MainActivity"], launchers
badging = pathlib.Path(sys.argv[2]).read_text()
assert "launchable-activity: name='com.yichincho.skcheck.MainActivity'" in badging
assert "股票提醒" in badging, "App name missing in packaged resources"
assert "application-icon" in badging, "Packaged launcher icon missing"
print("PASS: packaged release applicationId, app label, exported MAIN/LAUNCHER, icon and roundIcon")
