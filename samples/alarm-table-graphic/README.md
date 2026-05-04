# AlarmFilterRecord

Record for alarm filter criteria.

If a filter field is omitted, the default behavior is typically "all". For example, if `isCritical=true` is specified, only critical alarms are returned; if `isCritical` is omitted, both critical and non-critical alarms can be returned.

## Schema

| Field | Type | Description | Example |
|---|---|---|---|
| `atLocationOnly` | `boolean` | If `true`, only alarms at the specified location are selected; otherwise alarms at and below the location are selected. | `false` |
| `orderbyReceiptDate` | `boolean` | If present, orders alarm data by receipt date. Default ordering is by alarm record ID. | `true` |
| `includeCategories` | `string[]` | Limits selection to the specified category reference names. | `["hvac_critical", "firesystem_critical"]` |
| `excludeCategories` | `string[]` | Excludes specified category reference names. If `includeCategories` is present, this removes categories from the include set. | `["hvac_critical", "firesystem_critical"]` |
| `fromStates` | `string[]` | Limits selection to alarms transitioning from these states. | `["NORMAL", "FAULT", "OFF_NORMAL", "HIGH_LIMIT", "LOW_LIMIT"]` |
| `toStates` | `string[]` | Limits selection to alarms transitioning to these states. | `["NORMAL", "FAULT", "OFF_NORMAL", "HIGH_LIMIT", "LOW_LIMIT"]` |
| `byAcknowledgePending` | `boolean` | If present and `true`, selects alarms that are/are not still pending user acknowledgment. `false` has no effect. | `false` |
| `byAcknowledged` | `boolean` | If present and `true`, selects alarms that are acknowledged or do not require acknowledgment. `false` has no effect. | `false` |
| `byReturnToNormalPending` | `boolean` | If present and `true`, selects alarms that have/have not returned to normal. `false` has no effect. | `true` |
| `byClosed` | `boolean` | If present and `true`, selects alarms that are closed (returned to normal and acknowledged). `false` has no effect. | `false` |
| `isCritical` | `boolean` | Limits selection to alarms that are/are not classified as critical. | `null` |