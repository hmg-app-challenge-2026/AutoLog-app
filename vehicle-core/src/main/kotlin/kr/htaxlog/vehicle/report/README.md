# `kr.htaxlog.vehicle.report`

[앱 A] 운행기록부 XLSX와 검토 표시.

| 클래스 | 하는 일 |
|---|---|
| `ReportBuilder` | `ReportApi`: ① 운행기록부(국세청 양식 + 검토 열) ② 비용 ③ 요약 ④ 검토. 확정 목적만 업무거리, 셀 수식 없음, 시트 1 합계 = 시트 3 |
| `XlsxWriter` | POI 없이 SpreadsheetML + Zip |
| `ReviewCalculator` | 검토 7종(목적·운전자·거리 미확정, 일정–운행 불일치, 증빙 없음, 중복 영수증, 연료 이상치) |
| `MonthlySender`·`MonthlyReportWorker` | 월말 자동 생성·발송(매달 1~7일, 지난달 1회) |

파일: `filesDir/reports/운행기록부_{번호판}_{YYYYMM}.xlsx`.
