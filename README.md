# Be Quiet

音量を固定するためのアプリ
for Android

<img height="500" alt="Screenshot_20260628-055701" src="https://github.com/user-attachments/assets/91720f08-d178-4fd8-90ca-8fa7e0e11823" />


## Why?

〇〇PayやらP◯yP◯yやら、決済音を出すことを強制するのにデバイス自体のメディア音量をグローバルで上げてくるのですよ。
で、音量ゼロは無理だと。
そんなバカげた話許せないですよね。
じゃあ彼らと同じ手段で音量をグローバルでゼロにすればいいのではないか、ということです。

## How?

音量変化時にフックを...というのはレスポンスの関係で断念しました。
なので常駐で常に音量をゼロにする脳筋構成です。

そして音量ゼロだけではなく、音量レベル指定値での固定もできます。
途中、本アプリからの調整もできます。

## Install

- releaseからapkダウンロード
- ファイルからインストール

## 必要権限

- FOREGROUND_SERVICE
  - 常駐サービスのため
- POST_NOTIFICATIONS
  - 常駐通知、アプリ復帰用

<img width="500" alt="Screenshot_20260628-055729~2" src="https://github.com/user-attachments/assets/cbed31b5-532c-493c-b113-ae562c2a5b33" />
