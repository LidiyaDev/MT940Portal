# MT940 output format

Derived from the reference statement `1144355935012_20260923.txt`.

```
{1:F01DASHETAXXXXX0001020719}{2:I940RECVETAAXXXXN}{4:
:20:STMT260923935012
:25:1144355935012
:28C:20719/1
:60F:C260923ETB327060,15
:61:2609230923D2340,00NTRF879FXSA262660001
:86:CASH FCY BOUGHT AND SOLD
:62F:C260923ETB324720,15
-}
```

## Headers

| Block | Value | Rule |
|---|---|---|
| `{1:` | `F01` + `DASHETAXXXXX` + `0001` + `020719` + `}` | Application ID, 12 char sender LT address, 4 digit session, 6 digit ISN |
| `{2:` | `I940` + `RECVETAAXXXX` + `N` | Input MT940, 12 char receiver LT address, priority `N` |
| trailer | `-}` | Closes the `{4:` text block |

The ISN mirrors the `:28C:` statement number zero-padded to six digits
(`20719` → `020719`). Switch `mt940.swift.isn-strategy` to `STATIC` or
`ACCOUNT_SEQUENCE` if your SWIFT interface uses a different convention.

## Text block

| Tag | Example | Rule |
|---|---|---|
| `:20:` | `STMT260923935012` | `STMT` + YYMMDD of the statement date + last six characters of the account number, capped at 16 |
| `:25:` | `1144355935012` | Account number, sanitised to the SWIFT character set |
| `:28C:` | `20719/1` | Per-account statement number / page. Starts at the account's seed + 1 and increments on every generation |
| `:60F:` | `C260923ETB327060,15` | `C`/`D` mark + YYMMDD + currency + comma-decimal amount (always two decimals) |
| `:61:` | `2609230923D2340,00NTRF879FXSA262660001` | value date (YYMMDD), entry date (MMDD), D/C mark, *(funds code — omitted)*, 15d amount, `1!a3!c` type code, 16x customer reference |
| `:86:` | `CASH FCY BOUGHT AND SOLD` | Free text, up to 6 lines of 65 characters, wrapped at word boundaries |
| `:62F:` | `C260923ETB324720,15` | Closing balance. Equals opening − debits + credits |

Notes on the reference statement:

- **No funds code.** The third character of `ETB` would be `B`, but the sample omits it, so
  the default is `NONE`. Set `mt940.swift.funds-code-strategy: CURRENCY_THIRD_CHAR` to emit it.
- **No `:13D:`, `:90D:`, `:90C:`, `:64:` or `:65:`.** All disabled by default; each can be
  enabled globally or per client.
- **Balances are consistent:** `327060,15 − 2340,00 = 324720,15`. The generator derives the
  closing balance the same way when the feed does not supply one.
- **Line endings** are LF. SWIFT FIN does not permit empty lines inside the text block, so
  `blank-line-between-tags` defaults to `false`. If your consumer expects the blank lines that
  appear in some core banking exports, set it to `true` (or tick it on the client).

## Source data mapping

The core banking endpoint returns one row per entry:

```json
{ "transactionDate": "2026-09-02",
  "trans_reference": "8798799262450001 ACDB/TT/00971/26",
  "debit_amt": "20.00",
  "beginning_balance": "659110.01",
  "closing_balance": "659090.01",
  "txnDescription": "SERVICE CHARGE FOR CORESPONDENT BANK " }
```

| Source field | MT940 use |
|---|---|
| `transactionDate` | value date and entry date of `:61:`, and the statement date |
| `trans_reference` | 16x customer reference in `:61:` — upper-cased, whitespace stripped, truncated to 16 |
| `debit_amt` | amount + `D` mark. Treated as a debit when non-zero |
| `credit_amt` | amount + `C` mark. The mapping accepts `credit_amt`, `creditAmt`, `creditAmount` and `credit_amount` if the feed adds a credit column |
| `txnDescription` | `:86:` narrative. Trimmed, folded to the SWIFT character set, upper-cased |
| `beginning_balance` | opening balance `:60F:` — taken from the **first** row of the period |
| `closing_balance` | closing balance `:62F:` — taken from the **last** row of the period |

If the period returns no rows, the opening balance is carried forward from the previous
statement for that account and the closing balance equals it.

## Per-client overrides

Every SWIFT setting can be overridden per client (Clients screen → SWIFT overrides):

`senderLtAddress`, `receiverLtAddress`, `currency`, `fundsCodeStrategy`,
`openingBalanceTag`, `closingBalanceTag`, `emit13d`, `emit64`, `emit65`, `emit90d`,
`blankLineBetweenTags`, `statementNumberSeed`.

A transaction type map can also be defined globally:

```yaml
mt940:
  swift:
    transaction-type-rules:
      CHARGE: NCHG
      COMMISSION: NCHG
      TRANSFER: NTRF
```

The first matching keyword wins; otherwise `NTRF` is used.
