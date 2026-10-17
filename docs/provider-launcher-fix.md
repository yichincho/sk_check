# API 與 Launcher 修正

## 調查結果

- 本專案為原生 Kotlin / Jetpack Compose。Jetpack 不是 API 金鑰供應商；設定頁原有 NVIDIA、Gemini、DeepSeek。
- 原始 Manifest 已宣告 MAIN / LAUNCHER，MainActivity 存在且 exported=true，App 名稱為「股票提醒」。不能將圖示消失歸因於缺少 Activity 或入口。
- 原始圖示使用 drawable 中的 adaptive icon，minSdk=26；此配置本身不證明 APK 無法顯示圖示。此修正補上標準 mipmap、roundIcon 與向量備援，並驗證最終封裝內容。實機根因仍須手機上測試。
- NVIDIA 篩選器漏掉 GLM 視覺模型及 Nemotron Omni，預設候選過舊。驗證僅送文字、8 tokens，且將模型權限 403 誤當成金鑰一定無效；空回答也可能被當成成功。
- DeepSeek 只以名稱包含 vision / -vl 選擇模型，漏掉官方目前的 deepseek-flash。改讀 input_modalities，舊實驗版設定遷移至 deepseek-flash。

## 修改範圍

| 檔案 | 原因 |
| --- | --- |
| `data/Nvidia.kt` | 加入 GLM-5.3-Flash、GLM-V、Nemotron 3.5 Super VL / Nano Omni；沿用 API 回傳的 ID；測試圖片輸入；優先保存測通模型；區分 401 / 403 / 429；拒絕空回答。 |
| `data/Vision.kt` | DeepSeek 依圖片能力中繼資料選型；使用 deepseek-flash；實際圖片驗證；處理貼上的 Bearer 前綴。 |
| `data/Settings.kt` | 保留既有金鑰、其他設定，遷移 DeepSeek 舊模型名稱。 |
| `ui/SettingsScreen.kt` | 明確顯示 NVIDIA NIM API 與模型測試狀態，不宣稱所有候選皆測通。 |
| `AndroidManifest.xml`、`res/mipmap-anydpi*` | 保留既有權限、分享功能、Activity，補上一般與圓形圖示參照和資源。 |
| `ProviderTest.kt`、`app/build.gradle.kts` | 新增模型篩選、API 請求、權限備援、空回答與 401 的回歸測試。 |
| `.github/workflows/build.yml`、`scripts/*` | 檢查最終 Release Manifest、aapt badging；Android 10 / 15 模擬器安裝並從 Launcher intent 啟動；全部通過才發布。 |

applicationId 維持 `com.yichincho.skcheck`，名稱維持「股票提醒」，既有簽章配置維持不變。

## 驗證與限制

- 本機已執行 XML / 圖示參照 / applicationId 檢查、shell 語法檢查、Python 語法檢查及 git diff --check。
- 本機無法從外網下載 Gradle；編譯、JUnit 與模擬器測試交由 GitHub Actions。執行結果以該提交的 Actions 頁為準。
- `release-verification` artifact 包含封裝後 Manifest、badging 及 JUnit 報告；`launcher-api-29` / `launcher-api-35` 包含啟動解析、Activity 狀態、crash buffer 與截圖。
- 模擬器通過不表示已確認小米 / Redmi / POCO 實機桌面正常。啟動器也不保證自動新增桌面捷徑；應先在應用程式列表搜尋「股票提醒」。
- 未取得使用者實際 API 金鑰，不能宣稱其金鑰已實際連線成功。安裝後請在設定按「儲存並測試」，重新整理舊的模型清單。

## 官方模型依據（2026-10-17）

- https://build.nvidia.com/z-ai/glm-5-3-flash — API ID 是 `z-ai/glm-5.3-flash`（頁面 URL 與 API ID 不同），支援圖片。
- https://build.nvidia.com/z-ai/glm-5-3 — `z-ai/glm-5.3` 為純文字，不列入截圖辨識候選。
- https://build.nvidia.com/nvidia/nemotron-3.5-super-vl-120b-a12b
- https://api-docs.deepseek.com/api/list-models/ — `input_modalities` 列出圖片能力，`deepseek-flash` 支援圖片。
