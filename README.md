# Lark Connector - IntelliJ IDEA / Android Studio Plugin

**Lark Connector** seamlessly integrates your **Lark Base** or **Feishu Bitable** task management workspace directly inside Android Studio and IntelliJ IDEA.

---

## Key Features

- **Live REST API Sync**: Dynamically fetches tables (`GET /tables`), fields (`GET /fields`), and records (`GET /records`) from Lark Base.
- **Dynamic Table Grid**: Automatically renders headers and cells for all actual columns (e.g. *OKR*, *OKR Group*, *Task Leader*, *Start Date*, *End Date*, *Target Achieved*, etc.).
- **Schema-Aware Record Creation**:
  - **Select Fields**: Dropdown selectors populated with allowed options.
  - **Person Fields**: Person selectors populated with known team members.
  - **Date Fields**: Converts `YYYY/MM/DD` date strings to millisecond timestamps.
  - **Numeric Fields**: Client-side validation for numbers.
- **Auto-Refresh Timer**: Background auto-sync interval (**Off**, **Every 1 Min**, **Every 5 Min**, **Every 10 Min**).
- **Multi-Base Management**: Easily connect and switch between multiple Lark Base sheets.
- **Diagnostic Logging**: One-click error log copying and raw response viewer.

---

## Step-by-Step Setup Guide

### Step 1: Create an App in Lark Developer Console
1. Go to [Lark Developer Console](https://open.larksuite.com/app).
2. Click **Create Custom App** (or select an existing app).
3. In the left menu, click **Permissions & Scopes**.
4. Search for `bitable:app` and grant **View, comment, edit and manage Base** scope (add for both *Tenant Token* and *User Token*).

### Step 2: Publish Your App Version (Crucial Step!)
> ⚠️ **Important**: Newly added permission scopes do NOT take effect until an app version is published!
1. In Lark Developer Console, click **Create Version** in the orange banner (or go to **Version Management & Release**).
2. Enter version `1.0.0` and click **Save & Submit for Release** / **Publish**.
3. Once published (or created in **Test Companies & Users**), your write permissions will become active.

### Step 3: Get Your Access Token
1. Go to **Credentials & Test Notes** or **API Explorer** in Lark Developer Console.
2. Copy your **Tenant Access Token** (`t-...`).
   - *Note*: Tenant Access Token is recommended when the app is in test mode or internal distribution.

### Step 4: Connect in Android Studio / IntelliJ IDEA
1. Open Android Studio and click the **Lark Connector** tool window tab on the left sidebar.
2. Paste your **Lark Base URL** (copied from your browser address bar) and click **Continue**.
3. Paste your **Tenant Access Token** (`t-...`) and click **Connect & Test Workspace**.
4. Once connected, your live tables, dynamic headers, and records will be displayed on the dashboard!

### Step 5: Adding Additional Base Sheets
1. From the dashboard, select `➕ Connect Another Base Sheet...` or click `+ Add Base Sheet`.
2. Paste the new Lark Base URL and click **Continue**. Your saved access token will be auto-filled automatically!

---

## JetBrains Marketplace Publishing Guide

To upload this plugin to the JetBrains Marketplace:

### 1. Build the Plugin Archive File
In Android Studio Terminal or system command line, run:
```bash
./gradlew buildPlugin
```
The compiled plugin archive will be generated at:
```
build/distributions/LarkTaskPlugin-1.0.0.zip
```

### 2. Log In to JetBrains Marketplace
1. Navigate to [JetBrains Marketplace Plugin Portal](https://plugins.jetbrains.com/).
2. Log in with your JetBrains Account.

### 3. Upload Your Plugin
1. Click your profile icon at top right -> **Add Plugin** -> **Upload Plugin**.
2. Drag and drop `build/distributions/LarkTaskPlugin-1.0.0.zip` (or select the file from disk).
3. Fill in any additional details (License, Tags, Documentation URL) and submit.
4. JetBrains will perform automated verification and approve your plugin within 1-2 business days.
