# Stock Statement

A single-user Android app for maintaining a daily stock statement on the device. It works offline and needs no account.

## What it does

- Add, edit, and delete products, with a category and a price for each of Q, P, N, and D.
- Enter opening balance, receipts, and closing balance for each unit.
- Calculate sales as `opening balance + receipts - closing balance`; calculate each amount using that unit's price.
- Save a day and continue to the next date. The last saved closing balance for each product is prefilled as its next opening balance; all stock values remain editable.
- Export the statement as CSV for spreadsheet apps, or use Android's Print action to print or save a PDF.

Products and daily statements are stored locally on the device. Deleting a product also deletes its saved stock rows.

## Build

Open the project in Android Studio, or use Java 17, Android SDK 35, and Gradle 8.9:

```shell
gradle testDebugUnitTest assembleDebug
```

The installable debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Continuous integration

The GitHub Actions workflow runs tests and builds the debug APK on pushes, pull requests, or manual dispatch. Download `stock-statement-debug-apk` from the workflow run's **Artifacts** section. Push a version tag such as `v1.0.0` to also publish the APK as an asset on a GitHub Release. The attached APK is debug-signed for sideloading/testing, not Play Store distribution.
