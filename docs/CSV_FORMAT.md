# Xichu Finance Standard CSV

This is the only formally supported v1.0 import format. Raw WeChat, Alipay, and bank-export formats are **NOT VERIFIED**. Convert their columns to this standard first; their names in the example refer only to user-defined accounts.

## Format

- UTF-8, with or without a BOM. In Excel choose **CSV UTF-8**.
- Comma-separated columns; quote cells containing commas, quotes, or line breaks. Escape an embedded quote by doubling it.
- Maximum **512 KiB** and **500 data rows** per upload. Split larger files.
- Required headers: `date,description,amount,type,account`. Optional header: `category`. Unknown or duplicate headers are rejected.
- `date`: valid `YYYY-MM-DD`.
- `description`: nonempty, at most 500 characters.
- `amount`: positive CNY decimal, at most two fractional digits. No currency signs or thousands separators; income/expense direction comes from `type`.
- `type`: `income` or `expense`.
- `account`: exact name of an account already created by the logged-in user. Only a custom label is needed, never a card number.
- `category`: optional existing category with matching type. Without it, this phase uses that type's `其他` category.

```csv
date,description,amount,type,account
2026-09-01,早餐,15.00,expense,支付宝
2026-09-01,工资,6000.00,income,银行卡
2026-09-02,地铁,4.00,expense,微信
```

[sample_transactions.csv](../examples/sample_transactions.csv) contains fictional data. Create the three same-named accounts before trying it. Use your own transaction dates for current-month analytics.

## Import in Android

1. Log in to the cloud ledger and create the matching accounts/categories.
2. On the dashboard, open **CSV 账单导入** and choose a file through Android's document picker.
3. Selecting a file uploads its content to your Backend for validation. It does not save transactions yet.
4. Check the total, valid, error, and duplicate counts and row messages.
5. Tap **确认导入** to save only valid, nonduplicate rows. Error and duplicate rows are skipped. A preview expires after 15 minutes; select the file again if needed.
6. After confirmation, the app refreshes its Room cache. Offline import is not available; the local ledger remains a separate mode.

## Duplicates and privacy

The stable import hash includes user, account ID, date, normalized amount, type, description, CNY, and CSV source. `0.1` and `0.10` produce the same identity. Duplicate rows within one file and existing imported rows are skipped. The database unique constraint also protects repeated/concurrent commits. Manual transactions are separate from CSV identities.

Importing two genuinely different purchases with identical fingerprint fields will skip the second; distinguish their descriptions before importing. Deleting an imported transaction removes its identity, so importing that row later recreates it. Edited imported records retain their original import identity. Categories do not form part of the duplicate hash.

The Backend does not retain the raw CSV file after preview. Its signed preview token contains the validated rows and is kept only in Android memory until confirmation, logout, or process death; it is **not encrypted** and should be treated as private bookkeeping data. Production transport must use HTTPS. The token is bound to the authenticated user and cannot be used as a login token.

Successful commits store ordinary transactions in PostgreSQL. No original filenames, bank credentials, or complete card numbers are needed. Use fictional data in tests and screenshots.
