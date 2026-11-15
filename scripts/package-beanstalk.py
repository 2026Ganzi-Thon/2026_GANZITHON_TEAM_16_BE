"""Package only the executable JAR, Procfile and platform configuration."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

root = Path(__file__).resolve().parents[1]
jars = [p for p in (root / "build/libs").glob("*.jar") if not p.name.endswith("-plain.jar")]
if len(jars) != 1:
    raise SystemExit("Expected exactly one executable JAR; run gradlew bootJar first")
output = root / "build/beanstalk.zip"
with ZipFile(output, "w", ZIP_DEFLATED) as bundle:
    bundle.write(jars[0], "application.jar")
    bundle.write(root / "Procfile", "Procfile")
    for path in sorted((root / ".platform").rglob("*")):
        if path.is_file():
            bundle.write(path, path.relative_to(root).as_posix())
print(output)
