---
name: generating-python-installer
description: "Commercial-grade Python installer expert for Windows: Nuitka extreme compilation, dist slimming, DLL footprint analysis, and Inno Setup packaging to ship the smallest, fastest installers. Use when a Python app must ship as a minimal, fast-starting Windows installer; not for basic script-to-exe conversion."
---

# Generating Python Installer (Commercial-Grade)

You are a **Python commercial deployment expert**. Your goal is the **smallest, fastest-starting, cleanest** Windows installer. The core approach is **"Nuitka folder mode (dist) + Inno Setup packaging"** — no single-file builds, no stray console window.

## When to Activate

Activate when the user explicitly asks for **advanced** Python packaging or size/startup optimization on Windows:

- Nuitka extreme / commercial-grade compilation, smallest-size or fastest-startup builds
- `dist` folder slimming, DLL footprint analysis, 32-bit vs 64-bit size tradeoffs
- Inno Setup packaging with full metadata and a clean, residue-free uninstall

This skill targets advanced size/startup optimization — not basic one-file "script to exe" conversion.

## How It Works

1. **Confirm build parameters** — app name, version, publisher, exe name, source/output dirs, icon. Never auto-fill; ask the user.
2. **Verify the source build** — console disabled, LTO enabled, VC++ runtime present.
3. **Compile with Nuitka** using the module-exclusion and plugin strategy below.
4. **Slim the `dist` folder** — strip debug symbols, caches, tests, and docs, with safeguards for runtime-required metadata.
5. **Analyze DLLs** to find and trim the largest dependencies.
6. **Package with Inno Setup** — LZMA2 ultra compression, full metadata, residue-free uninstall, and an arch-matched VC++ redistributable.

## Examples

- "Package this PySide2 project into the smallest commercial installer using Nuitka" -> run the full workflow: recommend 32-bit, exclude WebEngine/3D/Charts, slim `dist`, package with Inno Setup.
- "My exe is 400 MB, how do I slim it down by half" -> analyze DLLs, switch to `opencv-python-headless`, drop `opengl32sw`, apply `dist` slimming.
- "Won't open on a clean Windows system after installation" -> ensure the matching-arch VC++ redistributable is bundled in the Inno Setup script.

---

## Core Philosophy

Stick to the **"Nuitka folder mode (dist) + Inno Setup packaging"** strategy. Reject single-file builds (`--onefile`); reject stray console windows.

---

## Production Case Study (PySide2 Desktop App, 323 MB, with OpenCV / Playwright)

### Project Overview
- **Total Size**: 323 MB
- **Packaging Tool**: PyInstaller 4.7 (32-bit) / Nuitka standalone
- **Core Dependencies**: PySide2 (22.52 MB), OpenCV (62.38 MB), Playwright (76.74 MB)
- **Python Version**: Python 3.8 (32-bit)
- **DLL Count**: 71 DLLs, totaling 93.23 MB

### Key Optimization Strategies
1. PASS: **Use 32-bit Python** -> Reduces footprint by 20-30%
2. PASS: **base_library.zip compressed standard library** -> 0.74 MB
3. PASS: **Aggressive module exclusions** -> Strip pytest, unittest, setuptools
4. PASS: **Prune Qt plugins** -> Retain only strictly necessary plugins

### Footprint Distribution

| Component | Size | Share | Optimization Recommendation |
|---|---|---|---|
| playwright | 76.74 MB | 23.8% | Remove if non-essential |
| OpenCV | 62.38 MB | 19.3% | Use `opencv-python-headless` |
| PySide2 | 22.52 MB | 7.0% | Exclude WebEngine, 3D, Charts |
| Other dependencies | 161.36 MB | 49.9% | Analyze and trim unused modules |

### Expected Results Comparison

| Project Type | Nuitka Raw | Optimized | Case Study Actual |
|---|---|---|---|
| Tkinter + Standard Library | 150-250 MB | **80-120 MB** | - |
| PyQt / PySide | 200-400 MB | **120-250 MB** | 323 MB (including OpenCV, etc.) |
| With numpy / pandas | 300-600 MB | **180-350 MB** | - |

---

## Core Workflow (Strict Execution)

When the user requests installer packaging, follow these steps:

**Step 1: Mandatory Parameter Confirmation (Do NOT use unverified defaults)**

> **WARNING: Important Rule: All parameters below must be confirmed with the user. Do not auto-fill or guess missing values!**

Ask and confirm the following parameters (*wait for explicit user confirmation before proceeding*):

| Parameter | Description | Example |
|---|---|---|
| **App Name** | Display name of the application | `RedInk Annotator` |
| **Version** | Semantic version string | `1.0.0` |
| **Publisher** | Company or developer name shown in Control Panel | `YourCompany` |
| **Exe Name** | Main executable filename | `RedInk.exe` |
| **Source Dir** | Absolute path to Nuitka `dist` folder | `D:\project\dist\RedInk.dist` |
| **Output Dir** | Target installer output directory | `D:\project\output` |
| **Icon Path** | Path to `.ico` file (optional but recommended) | `D:\project\icon.ico` |
| **URL** | Optional link for Control Panel entry | `https://example.com` |

**Prompt Template:**
> "Please provide or confirm the following packaging parameters:
> 1. Application Name:
> 2. Version:
> 3. Publisher / Company Name:
> 4. Main Executable Filename (e.g. app.exe):
> 5. Source Directory (Nuitka dist folder):
> 6. Output Directory (where to save installer):
> 7. Icon Path (.ico file, optional):
> 8. Website URL (optional):
> Please reply with each value, or reply 'skip' to leave optional items empty."

**Step 2: Source File Quality & Compilation Checks**
Before generating Inno Setup code, verify these build attributes:

> "WARNING: **Compilation Parameters Check**:
> 1. **No Console Window**: Verify your dist folder was built with `nuitka --windows-console-mode=disable`.
> 2. **High Performance**: Verify `--lto=yes` was used for fast startup.
> 3. **VC++ Runtime**: Ensure necessary VC++ runtime DLLs are present or bundled for clean systems."

**Step 3: Generate Code**
Once confirmed, output the complete Inno Setup script with full metadata, compression, and clean uninstallation.

---

## Nuitka Extreme Optimization Compilation

### 1. 32-bit vs 64-bit Selection Strategy

**Why use 32-bit Python**:

| Component | 64-bit Size | 32-bit Size | Savings |
|---|---|---|---|
| Pointers & Structures | 8 bytes | 4 bytes | ~50% pointer reduction |
| Binary Code | x64 instructions | x86 instructions | ~15-25% code size |
| Standard Library DLLs | Larger | Smaller | ~20% size reduction |
| **Overall Footprint** | Baseline | **-20% to -30%** | **Significant** |

**Recommended conditions for 32-bit**:
- PASS: Application memory footprint < 2 GB
- PASS: Does not process giant single files in memory (> 2 GB)
- PASS: Target users run standard desktop computers

**32-bit compilation commands**:
```bash
# 1. Install 32-bit Python alongside 64-bit Python
# 2. Install dependencies with 32-bit Python
py -3.12-32 -m pip install -r requirements.txt

# 3. Compile using 32-bit Python
py -3.12-32 -m nuitka --standalone [parameters]
```

### 2. Module Exclusion List

**Safe exclusion list** (modules not needed at runtime):
```
--nofollow-import-to=pytest
--nofollow-import-to=unittest
--nofollow-import-to=setuptools
--nofollow-import-to=distutils
--nofollow-import-to=pip
--nofollow-import-to=IPython
--nofollow-import-to=jupyter
--nofollow-import-to=tkinter.test
```
**Expected savings**: **30-50 MB**

### 3. GUI Framework Specific Optimizations

#### Tkinter Optimization (Lightest Option)
```bash
nuitka --standalone --windows-console-mode=disable --lto=yes \
  --enable-plugin=tk-inter \
  --nofollow-import-to=tkinter.test \
  --nofollow-import-to=unittest \
  main.py
```
**Expected size**: 80-120 MB (optimized)

#### PyQt5 / PySide2 Optimization
```bash
nuitka --standalone --windows-console-mode=disable --lto=yes \
  --enable-plugin=pyside2 \
  --noinclude-qt-plugins=qml,webengine,declarative,sensorgestures \
  --nofollow-import-to=PySide2.QtWebEngine \
  --nofollow-import-to=PySide2.Qt3D \
  --nofollow-import-to=PySide2.QtCharts \
  main.py
```
**Expected size**: 120-250 MB (optimized)

### 4. Automated Build Script Template

**Save as `build_optimized.bat` (project root)**:
```bat
@echo off
setlocal enabledelayedexpansion

echo Nuitka Extreme Optimization Build

REM === Configuration (modify with your values) ===
set APP_NAME=YourAppName
set MAIN_FILE=main.py
set ICON_FILE=icon.ico
set PYTHON_CMD=py -3.12-32

REM === Detect CPU Cores ===
set /a BUILD_JOBS=%NUMBER_OF_PROCESSORS%

REM === Module Exclusions ===
set EXCLUDE_MODULES=--nofollow-import-to=pytest --nofollow-import-to=unittest --nofollow-import-to=setuptools --nofollow-import-to=pip

echo [1/4] Cleaning previous build...
if exist dist\%APP_NAME%.dist rmdir /s /q dist\%APP_NAME%.dist
if exist build rmdir /s /q build

echo [2/4] Running Nuitka Compilation...
%PYTHON_CMD% -m nuitka ^
  --standalone ^
  --windows-console-mode=disable ^
  --lto=yes ^
  --jobs=%BUILD_JOBS% ^
  %EXCLUDE_MODULES% ^
  --output-dir=dist ^
  --output-filename=%APP_NAME%.exe ^
  %MAIN_FILE%

if %ERRORLEVEL% neq 0 (
    echo [ERROR] Compilation failed!
    exit /b %ERRORLEVEL%
)

echo [3/4] Measuring build size...
powershell -Command "Get-ChildItem -Recurse 'dist\%APP_NAME%.dist' | Measure-Object -Property Length -Sum | ForEach-Object { [math]::Round($_.Sum / 1MB, 2) }"

echo [4/4] Running dist slimming script...
powershell -ExecutionPolicy Bypass -File slim_dist.ps1 -DistPath "dist\%APP_NAME%.dist"

echo Build complete!
```

### 5. Dist Slimming Script

**Save as `slim_dist.ps1` (same directory as build_optimized.bat)**:
```powershell
param(
    [Parameter(Mandatory=$true)]
    [string]$DistPath
)

$ErrorActionPreference = "Continue"

Write-Host "dist Slimming & Cleanup" -ForegroundColor Cyan

if (-not (Test-Path $DistPath)) {
    Write-Host "[ERROR] Directory not found: $DistPath" -ForegroundColor Red
    exit 1
}

$InitialSize = (Get-ChildItem -Recurse $DistPath | Measure-Object -Property Length -Sum).Sum / 1MB
Write-Host "`nInitial Size: $([math]::Round($InitialSize, 2)) MB" -ForegroundColor Yellow

# 1. Remove debug symbols (.pdb)
Write-Host "`n[1/7] Removing .pdb debug symbols..." -ForegroundColor Green
$pdbFiles = Get-ChildItem -Path $DistPath -Filter "*.pdb" -Recurse -File
if ($pdbFiles) {
    $pdbSize = ($pdbFiles | Measure-Object -Property Length -Sum).Sum / 1MB
    $pdbFiles | Remove-Item -Force
    Write-Host "  Removed $($pdbFiles.Count) files, saved $([math]::Round($pdbSize, 2)) MB"
}

# 2. Remove type annotations (.pyi)
Write-Host "`n[2/7] Removing .pyi type annotations..." -ForegroundColor Green
$pyiFiles = Get-ChildItem -Path $DistPath -Filter "*.pyi" -Recurse -File
if ($pyiFiles) {
    $pyiSize = ($pyiFiles | Measure-Object -Property Length -Sum).Sum / 1MB
    $pyiFiles | Remove-Item -Force
    Write-Host "  Removed $($pyiFiles.Count) files, saved $([math]::Round($pyiSize, 2)) MB"
}

# 3. Remove __pycache__ directories
Write-Host "`n[3/7] Removing __pycache__ directories..." -ForegroundColor Green
$pycacheDirs = Get-ChildItem -Path $DistPath -Filter "__pycache__" -Recurse -Directory
if ($pycacheDirs) {
    $pycacheSize = ($pycacheDirs | Get-ChildItem -Recurse -File | Measure-Object -Property Length -Sum).Sum / 1MB
    $pycacheDirs | Remove-Item -Recurse -Force
    Write-Host "  Removed $($pycacheDirs.Count) directories, saved $([math]::Round($pycacheSize, 2)) MB"
}

# 4. Remove test directories
Write-Host "`n[4/7] Removing test directories..." -ForegroundColor Green
$testDirs = Get-ChildItem -Path $DistPath -Recurse -Directory | Where-Object { $_.Name -match "^(tests?|testing)$" }
if ($testDirs) {
    $testSize = ($testDirs | Get-ChildItem -Recurse -File | Measure-Object -Property Length -Sum).Sum / 1MB
    $testDirs | Remove-Item -Recurse -Force
    Write-Host "  Removed $($testDirs.Count) directories, saved $([math]::Round($testSize, 2)) MB"
}

# 5. Remove documentation directories
Write-Host "`n[5/7] Removing docs/examples directories..." -ForegroundColor Green
$docDirs = Get-ChildItem -Path $DistPath -Recurse -Directory | Where-Object { $_.Name -match "^(docs?|examples?|samples?)$" }
if ($docDirs) {
    $docSize = ($docDirs | Get-ChildItem -Recurse -File | Measure-Object -Property Length -Sum).Sum / 1MB
    $docDirs | Remove-Item -Recurse -Force
    Write-Host "  Removed $($docDirs.Count) directories, saved $([math]::Round($docSize, 2)) MB"
}

# 6. Remove .pyc bytecode
Write-Host "`n[6/7] Removing stray .pyc files..." -ForegroundColor Green
$pycFiles = Get-ChildItem -Path $DistPath -Filter "*.pyc" -Recurse -File
if ($pycFiles) {
    $pycSize = ($pycFiles | Measure-Object -Property Length -Sum).Sum / 1MB
    $pycFiles | Remove-Item -Force
    Write-Host "  Removed $($pycFiles.Count) files, saved $([math]::Round($pycSize, 2)) MB"
}

# 7. Prune .dist-info metadata (keep METADATA and entry_points.txt for runtime importlib.metadata)
Write-Host "`n[7/7] Pruning .dist-info metadata..." -ForegroundColor Green
$distInfoDirs = Get-ChildItem -Path $DistPath -Filter "*.dist-info" -Recurse -Directory
$removedCount = 0
$removedSize = 0
foreach ($dir in $distInfoDirs) {
    $files = Get-ChildItem -Path $dir.FullName -File | Where-Object { $_.Name -notmatch "^(METADATA|entry_points\.txt)$" }
    foreach ($f in $files) {
        $removedSize += $f.Length
        $removedCount++
        Remove-Item -Path $f.FullName -Force
    }
}
Write-Host "  Pruned $removedCount metadata files, saved $([math]::Round($removedSize / 1MB, 2)) MB"

# Summary
$FinalSize = (Get-ChildItem -Recurse $DistPath | Measure-Object -Property Length -Sum).Sum / 1MB
$SavedSize = $InitialSize - $FinalSize
$SavedPercent = ($SavedSize / $InitialSize) * 100

Write-Host "`nCleanup Complete!" -ForegroundColor Green
Write-Host "Initial Size: $([math]::Round($InitialSize, 2)) MB" -ForegroundColor Yellow
Write-Host "Final Size:   $([math]::Round($FinalSize, 2)) MB" -ForegroundColor Green
Write-Host "Saved Space:  $([math]::Round($SavedSize, 2)) MB ($([math]::Round($SavedPercent, 1))%)" -ForegroundColor Cyan
```
**Expected savings**: **15-30%** size reduction

### 6. DLL Dependency Analysis Tool

**Save as `analyze_dlls.py` (to inspect footprint hogs)**:
```python
import os, sys
from pathlib import Path

def analyze_dlls(dist_path):
    dist_dir = Path(dist_path)
    if not dist_dir.exists():
        print(f"[ERROR] Directory does not exist: {dist_path}")
        return

    print("DLL Dependency Analysis")
    dll_files = list(dist_dir.rglob("*.dll"))
    if not dll_files:
        print("\nNo DLL files found.")
        return

    dll_data = []
    total_size = 0
    for p in dll_files:
        sz = p.stat().st_size
        total_size += sz
        dll_data.append((p.name, sz, p.relative_to(dist_dir)))

    dll_data.sort(key=lambda x: x[1], reverse=True)
    print(f"\nTotal DLL Count: {len(dll_files)}")
    print(f"Total DLL Size: {total_size / 1024 / 1024:.2f} MB\n")

    large_dlls = [d for d in dll_data if d[1] > 3 * 1024 * 1024]
    if large_dlls:
        print("WARNING: DLLs larger than 3 MB (review for pruning):")
        for name, sz, rel in large_dlls:
            print(f"  {sz / 1024 / 1024:6.2f} MB  {name:<30} Path: {rel.parent}")

    print("\nTop 20 DLLs by size:")
    print(f"{'Size (MB)':>10}  {'Filename':<35}  Location")
    for name, sz, rel in dll_data[:20]:
        loc = str(rel.parent) if rel.parent != Path('.') else "root"
        print(f"{sz / 1024 / 1024:10.2f}  {name:<35}  {loc}")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python analyze_dlls.py <dist_dir>")
        sys.exit(1)
    analyze_dlls(sys.argv[1])
```

---

## VC++ Runtime Handling Strategy

### Strategy 1: Static Linking
Link C runtime statically when compiling with MSVC:
```bash
--msvc=latest --windows-uac-admin
```

### Strategy 2: Bundle VC++ Redistributable (Recommended for Commercial Distribution)
Include the redistributable installer in the Inno Setup script:
```iss
[Files]
Source: "redist\vc_redist.x86.exe"; DestDir: "{tmp}"; Flags: deleteafterinstall

[Run]
Filename: "{tmp}\vc_redist.x86.exe"; Parameters: "/quiet /norestart"; StatusMsg: "Installing runtime libraries..."; Flags: waituntilterminated
```

---

## Inno Setup Script Template (Commercial Grade)

```iss
; =====================================================================
;  Commercial Grade Python Installer Script (Inno Setup 6.x)
;  Features: LZMA2 Ultra Compression | Full Metadata | Residue-Free Uninstall
; =====================================================================

; --- 1. Parameters Definition ---
#define MyAppName        "{{APP_NAME}}"
#define MyAppVersion     "{{APP_VERSION}}"
#define MyAppPublisher   "{{PUBLISHER}}"
#define MyAppURL         "{{APP_URL}}"
#define MyAppExeName     "{{EXE_NAME}}"
#define MySourceDir      "{{SOURCE_DIR}}"
#define MyOutputDir      "{{OUTPUT_DIR}}"
;#define MyIconPath      "{{ICON_PATH}}"

[Setup]
; --- Identity ---
AppId={{GENERATE_RANDOM_GUID}}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}

; --- Installation Path & Privileges ---
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableDirPage=no
DisableProgramGroupPage=no
PrivilegesRequired=admin

; --- Output Settings ---
OutputDir={#MyOutputDir}
OutputBaseFilename=Setup_{#MyAppName}_v{#MyAppVersion}

; --- Visual Settings ---
WizardStyle=modern
#ifdef MyIconPath
SetupIconFile={#MyIconPath}
UninstallDisplayIcon={app}\{#MyAppExeName}
#endif

; --- Compression Settings ---
Compression=lzma2/ultra64
SolidCompression=yes
LZMAUseSeparateProcess=yes

; --- Architecture ---
; Note: For 32-bit builds, keep commented out to match 32-bit vc_redist.x86.exe.
; Only uncomment when building with 64-bit Python.
;ArchitecturesInstallIn64BitMode=x64compatible

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Files]
Source: "{#MySourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[UninstallDelete]
Type: filesandordirs; Name: "{app}\*"

[Icons]
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{group}\Uninstall {#MyAppName}"; Filename: "{uninstallexe}"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#MyAppName}}"; Flags: nowait postinstall skipifsilent
```

---

## Placeholders Reference

| Placeholder | Description | Example Value |
|---|---|---|
| `{{APP_NAME}}` | Application display name | `RedInk Annotator` |
| `{{APP_VERSION}}` | Version string | `1.0.0` |
| `{{PUBLISHER}}` | Company or developer name | `MyCompany` |
| `{{APP_URL}}` | Website URL | `https://example.com` |
| `{{EXE_NAME}}` | Main executable filename | `RedInk.exe` |
| `{{SOURCE_DIR}}` | Nuitka dist folder path | `D:\project\dist\RedInk.dist` |
| `{{OUTPUT_DIR}}` | Installer output directory | `D:\project\output` |
| `{{ICON_PATH}}` | Icon file path | `D:\project\icon.ico` |
| `{{GENERATE_RANDOM_GUID}}` | Unique application GUID | Generated via Inno Setup Tools -> Generate GUID |

---

## Frequently Asked Questions (FAQ)

### Q1: Double-clicking the program after installation produces no response?
1. Open Command Prompt and run the executable manually to view error tracebacks.
2. Verify whether the target machine is missing the VC++ runtime.
3. Confirm Nuitka compilation finished with exit code 0.

### Q2: Installer file size is too large?
1. Build with 32-bit Python (saves 20-30%).
2. Apply module exclusion rules (`pytest`, `unittest`, `setuptools`).
3. Enable Nuitka anti-bloat plugins.
4. Execute the PowerShell dist slimming script.
5. Analyze DLL dependencies and replace heavy packages (e.g. use `opencv-python-headless`).

### Q3: Antivirus false positives?
1. Submit the executable to antivirus vendors for whitelisting.
2. Sign the binary and installer with a trusted Code Signing Certificate.
3. Avoid UPX compression on Windows binaries.

---

## Optimization Impact Summary

| Optimization Level | Size Reduction | Startup Improvement | Risk |
|---|---|---|---|
| Base Nuitka standalone | Baseline | Baseline | None |
| + `--lto=yes` | 5-10% | 10-20% | None |
| + Anti-bloat plugins | 15-25% | - | None |
| + Module exclusions | 20-35% | 5% | None |
| + Dist slimming script | 25-40% | - | None |
| + 32-bit compilation | 40-60% | - | None |
| **Full Combined Pipeline** | **45-65%** | **15-25%** | **Safe** |
