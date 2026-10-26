"""Carry the fork's opt-in content shelf across an upstream launcher update.

Only the integration points live in upstream files. The shelf itself remains in
ContentRows.java. Stop if an anchor changes so a sync never drops the feature.
"""

from pathlib import Path


def replace_once(path: Path, before: str, after: str) -> None:
    source = path.read_text(encoding="utf-8")
    if source.count(before) != 1 or after in source:
        raise RuntimeError(f"Content shelf integration needs review: {path}")
    path.write_text(source.replace(before, after, 1), encoding="utf-8")


main = Path("launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java")
manifest = Path("launcher/app/src/main/AndroidManifest.xml")

replace_once(
    manifest,
    '    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />',
    '    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />\n'
    '    <uses-permission android:name="android.permission.READ_TV_LISTINGS" />\n'
    '    <uses-permission android:name="android.permission.INTERNET" />',
)
replace_once(
    main,
    "    private RecyclerView grid;",
    "    private RecyclerView grid;\n    private ContentRows contentRows;",
)
replace_once(
    main,
    "        grid = findViewById(R.id.grid);",
    "        grid = findViewById(R.id.grid);\n"
    "        contentRows = ContentRows.attach(this, grid);",
)
replace_once(
    main,
    "    protected void onResume() {\n        super.onResume();",
    "    protected void onResume() {\n        super.onResume();\n"
    "        if (contentRows != null) contentRows.refresh();",
)
replace_once(
    main,
    "    @Override\n    protected void onDestroy() {\n        super.onDestroy();",
    "    @Override\n"
    "    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {\n"
    "        super.onRequestPermissionsResult(requestCode, permissions, grantResults);\n"
    "        if (contentRows != null && requestCode == ContentRows.PERMISSION_REQUEST) contentRows.refresh();\n"
    "    }\n\n"
    "    @Override\n"
    "    protected void onDestroy() {\n"
    "        if (contentRows != null) contentRows.close();\n"
    "        super.onDestroy();",
)
