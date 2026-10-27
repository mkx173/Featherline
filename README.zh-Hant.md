<p align="center">
  <img src=".github/app-icon.png" alt="Featherline icon" width="120" />
</p>

# Featherline

[English](README.md) · [简体中文](README.zh-Hans.md) · **繁體中文**· [廣東話](README.zh-HK.md) 

適用於 Android 的 HRT 用藥記錄應用程式，具備 PK 藥物動力學曲線預測與血液檢查追蹤功能。資料存放於本機並加密，無需帳號。

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Build](https://github.com/mkx173/Featherline/actions/workflows/android-release.yml/badge.svg)](https://github.com/mkx173/Featherline/actions/workflows/android-release.yml)
![minSdk](https://img.shields.io/badge/minSdk-26-blue.svg)
![targetSdk](https://img.shields.io/badge/targetSdk-37-blue.svg)

Featherline 可記錄注射、貼片、凝膠、口服及舌下等給藥途徑的用藥；利用三室藥物動力學模型，依據你的用藥記錄預測雌二醇濃度；並可追蹤血液檢查結果，自動換算標準單位與臨床單位。所有資料都儲存在加密的本機資料庫中——不需要帳號、不收集遙測資料、不會發起任何網路連線。備份檔案會經過加密與壓縮。介面提供英文、簡體中文、繁體中文及廣東話。

> ⚠️ **本文不構成醫療建議。** Featherline 只是一個記錄工具，並非醫療器材，使用本應用程式也不會建立醫病關係。藥物動力學曲線僅是根據你記錄的劑量所做出的粗略族群平均估算，並可選擇依給藥途徑對齊你自己的雌二醇檢查結果——但它不能取代抽血檢查，也不能取代醫師的判讀，請勿因此調整用藥。完整免責聲明請參閱 [docs/safety.md](docs/safety.md)。

## 取得應用程式

<a href="https://play.google.com/store/apps/details?id=com.mkx.hrttracker"><img src=".github/GetItOnGooglePlay_Badge_Web_color_English.png" alt="Get it on Google Play" height="54" align="middle"></a>
<a href="https://f-droid.org/packages/com.mkx.hrttracker"><img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="80" align="middle"></a>

- **Play Store**：[連結](https://play.google.com/store/apps/details?id=com.mkx.hrttracker)
- **F-Droid**：[連結](https://f-droid.org/packages/com.mkx.hrttracker)
- **GitHub Releases**（已簽署 APK，可側載安裝）：[releases 頁面](https://github.com/mkx173/Featherline/releases)
- 或從原始碼建置：請參閱 [docs/building.md](docs/building.md)

> **注意：** 建議使用 Play Store 或 F-Droid 版本。本應用程式未申請網路權限，因此 GitHub 上的 APK 無法自動檢查更新——有新版本時請手動留意 releases 頁面。

## 功能特色

- 記錄注射、貼片、凝膠、口服及舌下等給藥途徑的用藥
- 可自訂的提醒排程，支援稍後提醒與精確鬧鐘處理
- 將藥物分組，並對組內用藥套用排程
- 選配的藥物庫存追蹤，提供庫存不足警告，以及配合排程計算的「剩餘天數」估算
- 依據用藥記錄預測雌二醇藥物動力學曲線
- 估算校準：依給藥途徑將雌二醇預測曲線對齊你自己的 E2 檢查結果，並提供各途徑的信賴度、異常結果複核清單，以及主頁圖表上的預測區間
- 血液檢查項目目錄，自動換算單位（pg/mL ↔ pmol/L、ng/dL ↔ nmol/L）
- 加密、壓縮的備份格式，附完整還原驗證
- 應用程式鎖，支援生物辨識解鎖
- 主畫面快速記錄小工具，提供兩種尺寸，顯示進度、下一次用藥，點擊即可記錄
- 日記分頁：以時間軸記錄重要日子與里程碑，也可記錄每日心得；任何日期都能釘選到主畫面，成為顯示累計天數的時間軸小工具或捷徑
- 不需要帳號、不收集遙測資料、不會發起網路連線——所有資料都留在裝置上
- 英文、簡體中文、繁體中文及廣東話介面
- Material 3 動態取色

## 螢幕截圖

<table>
  <tr>
    <td width="25%"><img src=".github/screenshots/home.png" alt="主頁"></td>
    <td width="25%"><img src=".github/screenshots/plan.png" alt="計劃頁面"></td>
    <td width="25%"><img src=".github/screenshots/history.png" alt="歷史記錄"></td>
    <td width="25%"><img src=".github/screenshots/calibration.png" alt="校準頁面"></td>
  </tr>
  <tr>
    <td align="center">主頁</td>
    <td align="center">計劃</td>
    <td align="center">歷史</td>
    <td align="center">校準</td>
  </tr>
</table>

## 運作原理

Featherline 是一個以 Kotlin 與 Jetpack Compose 撰寫的單一模組 Android 應用程式。用藥、提醒與檢查結果都儲存於經 SQLCipher 加密的 Room 資料庫。藥物動力學引擎採用三室模型，將每一筆記錄的用藥隨時間轉換為對應的雌二醇貢獻，再將所有給藥途徑的貢獻加總，得出預測曲線。當你記錄 E2 檢查結果時，裝置內的配適程式會依各途徑縮放貢獻值以貼合結果，並在調整後的曲線周圍繪出預測區間。提醒功能使用 AlarmManager，具備精確鬧鐘權限處理與稍後提醒支援；透過對帳機制，通知在裝置重新開機與時區變更後依然準確。血液檢查目錄以標準因子表為各分析項定義雙向單位換算。備份檔案經過加密與壓縮，並採用帶版本號的格式，附完整的還原驗證。

完整的架構、資料模型與提醒流程請參閱 [docs/architecture.md](docs/architecture.md)。

## 路線圖

- 以更通用的多藥物模型取代現有藥物動力學引擎
- 選配的加密雲端備份（預設關閉，端對端加密）
- 更多語言——歡迎在 [Hosted Weblate](https://hosted.weblate.org/projects/featherline/) 參與翻譯（請參閱 [docs/localization.md](docs/localization.md)）

## 技術棧

Kotlin、Jetpack Compose（Material 3）、Hilt 相依性注入、Room 搭配 SQLCipher 加密儲存、Coroutines + Flow。確切版本請參閱 [gradle/libs.versions.toml](gradle/libs.versions.toml)。

## 從原始碼建置

系統需求：JDK 17、較新版本的 Android Studio，以及 API 級別符合本專案 `compileSdk` 的 Android SDK。以 Android Studio 標準流程匯入專案，執行 `./gradlew assembleDebug`，或直接使用 IDE 建置。

詳細說明、建置風味與 CI 行為：請參閱 [docs/building.md](docs/building.md)。

## 參與貢獻

歡迎貢獻。請先閱讀 [行為準則](CODE_OF_CONDUCT.md)，再參閱 [CONTRIBUTING.md](CONTRIBUTING.md) 了解開發流程、分支慣例與提案方式。

歡迎在 [Hosted Weblate](https://hosted.weblate.org/projects/featherline/) 參與翻譯。

## 隱私

Featherline 將所有資料以加密形式儲存在你的裝置上，沒有帳號、沒有遙測、沒有網路連線。完整的資料處理說明請參閱 [docs/privacy.md](docs/privacy.md)。

## 授權條款

Featherline 以 GNU 通用公共授權條款 3.0 版（GPL v3）發布。完整條文請參閱 [LICENSE](LICENSE)。
第三方套件、素材與改寫程式碼的聲明，請參閱 [docs/third-party-notices.md](docs/third-party-notices.md)。

## 致謝

- [Material Symbols](https://fonts.google.com/icons) 圖示集。
- 藥物動力學預測參考了 [HRT-Recorder-PKcomponent-Test](https://github.com/LaoZhong-Mihari/HRT-Recorder-PKcomponent-Test) 的數學參考資料。
- 十一酸雌二醇的藥物動力學參數來自 [Transmtf-HRT-Tracker](https://github.com/TransmtfTeam/Transmtf-HRT-Tracker)。
- 圖表顯示邏輯改編自 [Oyama's HRT Tracker](https://github.com/SmirnovaOyama/Oyama-s-HRT-Tracker)。
- 更廣泛的跨性別健康社群——感謝他們的測試、回饋與先前研究，讓這類工具得以實現。
