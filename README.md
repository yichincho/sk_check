# sk_check · 股票提醒

台股觀察與提醒 App（Android）。用 Yahoo Finance 報價（非即時），到達你設的價格或漲跌幅時跳通知，讓你去看一下再決定要不要買；每次買進、賣出和截圖都留下理由。

## 它會做什麼

- **三種方式加入觀察**：輸入代號（可一次多個，上市自動補 `.TW`、上櫃補 `.TWO`）、匯入 CSV、貼上截圖。
- **四種警示**：價格 ≤、價格 ≥、下跌 x%、上漲 x%。漲跌幅的基準可以是加入時的價格、截圖上的價格、買進價、昨收或自訂。
- **用當天高低點判斷**：下跌類（價格 ≤、下跌 %）看當天最低價，上漲類（價格 ≥、上漲 %）看當天最高價；只要盤中曾經碰到就算到價，現價只供參考。
- **背景檢查**：只在台股盤中（週一到週五 09:00–13:30）檢查，可選每 5／10／15 分鐘。條件成立時預設每小時最多通知一次、一天最多 3 次（可在設定改）。
- **截圖紀錄**：截圖原檔、AI 讀出的內容和你的說明存在一起；勾選要加入觀察的股票。截圖可以從相簿或其他 App「分享」進來。
- **買賣理由**：記錄買或賣、價格、股數、理由標籤和說明；日誌可依買賣／截圖／通知篩選，買賣可匯出 CSV。

## 安裝

到 [Releases](https://github.com/yichincho/sk_check/releases/latest) 下載 `sk-check.apk`，在手機上安裝（Android 8.0 以上）。第一次開啟時請允許通知。

截圖辨識可以用三家 AI，依序 NVIDIA → Gemini → DeepSeek，有填金鑰的才會用，一家失敗自動換下一家。在 App「設定」貼上金鑰、按「儲存並測試」：

| 金鑰 | 申請 | 說明 |
| --- | --- | --- |
| NVIDIA | [build.nvidia.com](https://build.nvidia.com) | 測試時抓最新清單，挑出能看圖的熱門 Top 5 |
| Gemini | [aistudio.google.com](https://aistudio.google.com) | 備案，預設 `gemini-flash-latest` |
| DeepSeek | [platform.deepseek.com](https://platform.deepseek.com) | 備案，只有看圖專用模型（`deepseek-v4-flash-vision-exp`，實驗版）能讀圖 |

一把都沒有也能存截圖和說明，只是不會自動讀出股票。

金鑰只存在手機裡，不會進到這個 repo。

## CSV 格式

第一列是標題，只有「代號」必填，空白的跌幅／漲幅用設定裡的預設值：

```
代號,名稱,目標買價,目標賣價,跌幅%,漲幅%,備註
2330,台積電,1000,,5,10,等法說後回檔
2317,鴻海,190,230,,,
```

UTF-8 或 Big5 都讀得到。已在清單的代號會更新規則，不會重複加入。

## 已知限制

- Yahoo Finance 的台股報價約延遲 20 分鐘；這是非官方介面，可能改版或暫時擋請求（查太快會回 429），App 會顯示錯誤、稍後再試，不會誤發通知。
- Android 背景排程最穩定的間隔是 15 分鐘。選 5 或 10 分鐘時，手機省電模式可能把檢查延後，建議在「設定」把本 App 設為不受電池最佳化限制。
- 沒有排除國定假日；休市日報價不變，同一條規則一天也最多只通知一次。

## 開發

```bash
./gradlew testDebugUnitTest assembleRelease
```

推到 `main` 之後，GitHub Actions 會建置 APK 並更新 Releases 的 `latest`。每次建置的結果和錯誤摘要會推到 `build-logs` 分支（`status.txt`、`errors.txt`、`build.log`）。

`signing/app.keystore` 是故意放進 repo 的固定簽章檔（密碼寫在 `app/build.gradle.kts`），讓每次建置的 APK 都能直接覆蓋安裝。它不是機密；如果要上架商店，請換成自己保管的金鑰。
